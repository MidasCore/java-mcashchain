package io.midasprotocol.core.net.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.midasprotocol.common.overlay.discover.node.statistics.MessageCount;
import io.midasprotocol.common.overlay.message.Message;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.common.utils.Time;
import io.midasprotocol.core.capsule.BlockCapsule.BlockId;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.net.TronNetDelegate;
import io.midasprotocol.core.net.message.BlockMessage;
import io.midasprotocol.core.net.message.FetchInvDataMessage;
import io.midasprotocol.core.net.message.InventoryMessage;
import io.midasprotocol.core.net.message.TransactionMessage;
import io.midasprotocol.core.net.peer.Item;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.midasprotocol.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static io.midasprotocol.core.config.Parameter.NetConstants.MAX_TRX_FETCH_PER_PEER;
import static io.midasprotocol.core.config.Parameter.NetConstants.MSG_CACHE_DURATION_IN_BLOCKS;

@Slf4j
@Component
public class AdvService {

	@Autowired
	private TronNetDelegate tronNetDelegate;

	private ConcurrentHashMap<Item, Long> invToFetch = new ConcurrentHashMap<>();

	private ConcurrentHashMap<Item, Long> invToSpread = new ConcurrentHashMap<>();

	private Cache<Item, Long> invToFetchCache = CacheBuilder.newBuilder()
			.maximumSize(100_000).expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();

	private Cache<Item, Message> trxCache = CacheBuilder.newBuilder()
			.maximumSize(50_000).expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();

	private Cache<Item, Message> blockCache = CacheBuilder.newBuilder()
			.maximumSize(10).expireAfterWrite(1, TimeUnit.MINUTES).recordStats().build();

	private ScheduledExecutorService spreadExecutor = Executors.newSingleThreadScheduledExecutor();

	private ScheduledExecutorService fetchExecutor = Executors.newSingleThreadScheduledExecutor();

	@Getter
	private MessageCount trxCount = new MessageCount();

	private boolean fastForward = Args.getInstance().isFastForward();

	public void init() {
		if (!fastForward) {
			spreadExecutor.scheduleWithFixedDelay(() -> {
				try {
					consumerInvToSpread();
				} catch (Throwable t) {
					logger.error("Spread thread error.", t);
				}
			}, 100, 10, TimeUnit.MILLISECONDS);
		}

		fetchExecutor.scheduleWithFixedDelay(() -> {
			try {
				consumerInvToFetch();
			} catch (Throwable t) {
				logger.error("Fetch thread error.", t);
			}
		}, 100, 10, TimeUnit.MILLISECONDS);
	}

	public void close() {
		spreadExecutor.shutdown();
		fetchExecutor.shutdown();
	}

	synchronized public boolean addInv(Item item) {
		if (invToFetchCache.getIfPresent(item) != null) {
			return false;
		}

		if (item.getType().equals(InventoryType.TRX)) {
			if (trxCache.getIfPresent(item) != null) {
				return false;
			}
		} else {
			if (blockCache.getIfPresent(item) != null) {
				return false;
			}
		}

		invToFetchCache.put(item, System.currentTimeMillis());
		invToFetch.put(item, System.currentTimeMillis());
		return true;
	}

	public Message getMessage(Item item) {
		if (item.getType().equals(InventoryType.TRX)) {
			return trxCache.getIfPresent(item);
		} else {
			return blockCache.getIfPresent(item);
		}
	}

	public void broadcast(Message msg) {
		Item item;
		if (msg instanceof BlockMessage) {
			BlockMessage blockMsg = (BlockMessage) msg;
			item = new Item(blockMsg.getMessageId(), InventoryType.BLOCK);
			logger.info("Ready to broadcast block {}", blockMsg.getBlockId().getString());
			blockMsg.getBlockCapsule().getTransactions().forEach(transactionCapsule -> {
				Sha256Hash tid = transactionCapsule.getTransactionId();
				invToSpread.remove(tid);
				trxCache.put(new Item(tid, InventoryType.TRX),
						new TransactionMessage(transactionCapsule.getInstance()));
			});
			blockCache.put(item, msg);
		} else if (msg instanceof TransactionMessage) {
			TransactionMessage trxMsg = (TransactionMessage) msg;
			item = new Item(trxMsg.getMessageId(), InventoryType.TRX);
			trxCount.add();
			trxCache.put(item,
					new TransactionMessage(((TransactionMessage) msg).getTransactionCapsule().getInstance()));
		} else {
			logger.error("Adv item is neither block nor trx, type: {}", msg.getType());
			return;
		}
		synchronized (invToSpread) {
			invToSpread.put(item, System.currentTimeMillis());
		}

		if (fastForward) {
			consumerInvToSpread();
		}
	}

	public void onDisconnect(PeerConnection peer) {
		if (!peer.getAdvInvRequest().isEmpty()) {
			peer.getAdvInvRequest().keySet().forEach(item -> {
				if (tronNetDelegate.getActivePeer().stream()
						.anyMatch(p -> !p.equals(peer) && p.getAdvInvReceive().getIfPresent(item) != null)) {
					invToFetch.put(item, System.currentTimeMillis());
				} else {
					invToFetchCache.invalidate(item);
				}
			});
		}
	}

	private void consumerInvToFetch() {
		Collection<PeerConnection> peers = tronNetDelegate.getActivePeer().stream()
				.filter(peer -> peer.isIdle())
				.collect(Collectors.toList());

		if (invToFetch.isEmpty() || peers.isEmpty()) {
			return;
		}

		InvSender invSender = new InvSender();
		long now = System.currentTimeMillis();
		invToFetch.forEach((item, time) -> {
			if (time < now - MSG_CACHE_DURATION_IN_BLOCKS * BLOCK_PRODUCED_INTERVAL) {
				logger.info("This obj is too late to fetch, type: {} hash: {}.", item.getType(),
						item.getHash());
				invToFetch.remove(item);
				invToFetchCache.invalidate(item);
				return;
			}
			peers.stream()
					.filter(peer -> peer.getAdvInvReceive().getIfPresent(item) != null
							&& invSender.getSize(peer) < MAX_TRX_FETCH_PER_PEER)
					.sorted(Comparator.comparingInt(peer -> invSender.getSize(peer)))
					.findFirst().ifPresent(peer -> {
				invSender.add(item, peer);
				peer.getAdvInvRequest().put(item, now);
				invToFetch.remove(item);
			});
		});

		invSender.sendFetch();
	}

	private void consumerInvToSpread() {
		if (invToSpread.isEmpty()) {
			return;
		}

		InvSender invSender = new InvSender();
		HashMap<Item, Long> spread = new HashMap<>();
		synchronized (invToSpread) {
			spread.putAll(invToSpread);
			invToSpread.clear();
		}

		tronNetDelegate.getActivePeer().stream()
				.filter(peer -> !peer.isNeedSyncFromPeer() && !peer.isNeedSyncFromUs())
				.forEach(peer -> spread.entrySet().stream()
						.filter(entry -> peer.getAdvInvReceive().getIfPresent(entry.getKey()) == null
								&& peer.getAdvInvSpread().getIfPresent(entry.getKey()) == null)
						.forEach(entry -> {
							peer.getAdvInvSpread().put(entry.getKey(), Time.getCurrentMillis());
							invSender.add(entry.getKey(), peer);
						}));

		invSender.sendInv();
	}

	class InvSender {

		private HashMap<PeerConnection, HashMap<InventoryType, LinkedList<Sha256Hash>>> send = new HashMap<>();

		public void clear() {
			this.send.clear();
		}

		public void add(Entry<Sha256Hash, InventoryType> id, PeerConnection peer) {
			if (send.containsKey(peer) && !send.get(peer).containsKey(id.getValue())) {
				send.get(peer).put(id.getValue(), new LinkedList<>());
			} else if (!send.containsKey(peer)) {
				send.put(peer, new HashMap<>());
				send.get(peer).put(id.getValue(), new LinkedList<>());
			}
			send.get(peer).get(id.getValue()).offer(id.getKey());
		}

		public void add(Item id, PeerConnection peer) {
			if (send.containsKey(peer) && !send.get(peer).containsKey(id.getType())) {
				send.get(peer).put(id.getType(), new LinkedList<>());
			} else if (!send.containsKey(peer)) {
				send.put(peer, new HashMap<>());
				send.get(peer).put(id.getType(), new LinkedList<>());
			}
			send.get(peer).get(id.getType()).offer(id.getHash());
		}

		public int getSize(PeerConnection peer) {
			if (send.containsKey(peer)) {
				return send.get(peer).values().stream().mapToInt(LinkedList::size).sum();
			}
			return 0;
		}

		public void sendInv() {
			send.forEach((peer, ids) -> ids.forEach((key, value) -> {
				if (key.equals(InventoryType.TRX) && peer.isFastForwardPeer()) {
					return;
				}
				if (key.equals(InventoryType.BLOCK)) {
					value.sort(Comparator.comparingLong(value1 -> new BlockId(value1).getNum()));
				}
				peer.sendMessage(new InventoryMessage(value, key));
			}));
		}

		void sendFetch() {
			send.forEach((peer, ids) -> ids.forEach((key, value) -> {
				if (key.equals(InventoryType.BLOCK)) {
					value.sort(Comparator.comparingLong(value1 -> new BlockId(value1).getNum()));
				}
				peer.sendMessage(new FetchInvDataMessage(value, key));
			}));
		}
	}

}
