package io.midasprotocol.core.net.messagehandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.capsule.BlockCapsule.BlockId;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.P2pException;
import io.midasprotocol.core.exception.P2pException.TypeEnum;
import io.midasprotocol.core.net.TronNetDelegate;
import io.midasprotocol.core.net.message.BlockMessage;
import io.midasprotocol.core.net.message.TronMessage;
import io.midasprotocol.core.net.peer.Item;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.core.net.service.AdvService;
import io.midasprotocol.core.net.service.SyncService;
import io.midasprotocol.core.services.WitnessProductBlockService;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;

import static io.midasprotocol.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static io.midasprotocol.core.config.Parameter.ChainConstant.BLOCK_SIZE;

@Slf4j
@Component
public class BlockMsgHandler implements TronMsgHandler {

	@Autowired
	private TronNetDelegate tronNetDelegate;

	@Autowired
	private AdvService advService;

	@Autowired
	private SyncService syncService;

	@Autowired
	private WitnessProductBlockService witnessProductBlockService;

	private int maxBlockSize = BLOCK_SIZE + 1000;

	private boolean fastForward = Args.getInstance().isFastForward();

	@Override
	public void processMessage(PeerConnection peer, TronMessage msg) throws P2pException {

		BlockMessage blockMessage = (BlockMessage) msg;

		check(peer, blockMessage);

		BlockId blockId = blockMessage.getBlockId();
		Item item = new Item(blockId, InventoryType.BLOCK);
		if (peer.getSyncBlockRequested().containsKey(blockId)) {
			peer.getSyncBlockRequested().remove(blockId);
			syncService.processBlock(peer, blockMessage);
		} else {
			peer.getAdvInvRequest().remove(item);
			processBlock(peer, blockMessage.getBlockCapsule());
		}
	}

	private void check(PeerConnection peer, BlockMessage msg) throws P2pException {
		Item item = new Item(msg.getBlockId(), InventoryType.BLOCK);
		if (!peer.getSyncBlockRequested().containsKey(msg.getBlockId()) && !peer.getAdvInvRequest()
				.containsKey(item)) {
			throw new P2pException(TypeEnum.BAD_MESSAGE, "no request");
		}
		BlockCapsule blockCapsule = msg.getBlockCapsule();
		if (blockCapsule.getInstance().getSerializedSize() > maxBlockSize) {
			throw new P2pException(TypeEnum.BAD_MESSAGE, "block size over limit");
		}
		long gap = blockCapsule.getTimeStamp() - System.currentTimeMillis();
		if (gap >= BLOCK_PRODUCED_INTERVAL) {
			throw new P2pException(TypeEnum.BAD_MESSAGE, "block time error");
		}
	}

	private void processBlock(PeerConnection peer, BlockCapsule block) throws P2pException {
		BlockId blockId = block.getBlockId();
		if (!tronNetDelegate.containBlock(block.getParentBlockId())) {
			logger.warn("Get unlink block {} from {}, head is {}.", blockId.getString(),
					peer.getInetAddress(), tronNetDelegate
							.getHeadBlockId().getString());
			syncService.startSync(peer);
			return;
		}

		if (fastForward && tronNetDelegate.validBlock(block)) {
			advService.broadcast(new BlockMessage(block));
		}

		tronNetDelegate.processBlock(block);
		witnessProductBlockService.validWitnessProductTwoBlock(block);
		tronNetDelegate.getActivePeer().forEach(p -> {
			if (p.getAdvInvReceive().getIfPresent(blockId) != null) {
				p.setBlockBothHave(blockId);
			}
		});

		if (!fastForward) {
			advService.broadcast(new BlockMessage(block));
		}
	}

}
