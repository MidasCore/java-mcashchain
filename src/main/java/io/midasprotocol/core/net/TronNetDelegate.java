package io.midasprotocol.core.net;

import io.midasprotocol.common.overlay.message.Message;
import io.midasprotocol.common.overlay.server.SyncPool;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.capsule.BlockCapsule.BlockId;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.db.WitnessStore;
import io.midasprotocol.core.exception.*;
import io.midasprotocol.core.exception.P2pException.TypeEnum;
import io.midasprotocol.core.net.message.BlockMessage;
import io.midasprotocol.core.net.message.MessageTypes;
import io.midasprotocol.core.net.message.TransactionMessage;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
public class TronNetDelegate {

	@Autowired
	private SyncPool syncPool;

	@Autowired
	private Manager dbManager;

	@Autowired
	private WitnessStore witnessStore;

	@Getter
	private Object blockLock = new Object();

	private int blockIdCacheSize = 100;

	private Queue<BlockId> freshBlockId = new ConcurrentLinkedQueue<BlockId>() {
		@Override
		public boolean offer(BlockId blockId) {
			if (size() > blockIdCacheSize) {
				super.poll();
			}
			return super.offer(blockId);
		}
	};

	public Collection<PeerConnection> getActivePeer() {
		return syncPool.getActivePeers();
	}

	public long getSyncBeginNumber() {
		return dbManager.getSyncBeginNumber();
	}

	public long getBlockTime(BlockId id) throws P2pException {
		try {
			return dbManager.getBlockById(id).getTimeStamp();
		} catch (BadItemException | ItemNotFoundException e) {
			throw new P2pException(TypeEnum.DB_ITEM_NOT_FOUND, id.getString());
		}
	}

	public BlockId getHeadBlockId() {
		return dbManager.getHeadBlockId();
	}

	public BlockId getSolidBlockId() {
		return dbManager.getSolidBlockId();
	}

	public BlockId getGenesisBlockId() {
		return dbManager.getGenesisBlockId();
	}

	public BlockId getBlockIdByNum(long num) throws P2pException {
		try {
			return dbManager.getBlockIdByNum(num);
		} catch (ItemNotFoundException e) {
			throw new P2pException(TypeEnum.DB_ITEM_NOT_FOUND, "num: " + num);
		}
	}

	public BlockCapsule getGenesisBlock() {
		return dbManager.getGenesisBlock();
	}

	public long getHeadBlockTimeStamp() {
		return dbManager.getHeadBlockTimeStamp();
	}

	public boolean containBlock(BlockId id) {
		return dbManager.containBlock(id);
	}

	public boolean containBlockInMainChain(BlockId id) {
		return dbManager.containBlockInMainChain(id);
	}

	public LinkedList<BlockId> getBlockChainHashesOnFork(BlockId forkBlockHash) throws P2pException {
		try {
			return dbManager.getBlockChainHashesOnFork(forkBlockHash);
		} catch (NonCommonBlockException e) {
			throw new P2pException(TypeEnum.HARD_FORKED, forkBlockHash.getString());
		}
	}

	public boolean canChainRevoke(long num) {
		return num >= dbManager.getSyncBeginNumber();
	}

	public boolean contain(Sha256Hash hash, MessageTypes type) {
		if (type.equals(MessageTypes.BLOCK)) {
			return dbManager.containBlock(hash);
		} else if (type.equals(MessageTypes.TRX)) {
			return dbManager.getTransactionStore().has(hash.getBytes());
		}
		return false;
	}

	public Message getData(Sha256Hash hash, InventoryType type) throws P2pException {
		try {
			switch (type) {
				case BLOCK:
					return new BlockMessage(dbManager.getBlockById(hash));
				case TRX:
					TransactionCapsule tx = dbManager.getTransactionStore().get(hash.getBytes());
					if (tx != null) {
						return new TransactionMessage(tx.getInstance());
					}
					throw new StoreException();
				default:
					throw new StoreException();
			}
		} catch (StoreException e) {
			throw new P2pException(TypeEnum.DB_ITEM_NOT_FOUND,
				"type: " + type + ", hash: " + hash.getByteString());
		}
	}

	public void processBlock(BlockCapsule block) throws P2pException {
		synchronized (blockLock) {
			try {
				if (!freshBlockId.contains(block.getBlockId())) {
					dbManager.pushBlock(block);
					freshBlockId.add(block.getBlockId());
					logger.info("Success process block {}.", block.getBlockId().getString());
				}
			} catch (ValidateSignatureException
				| ContractValidateException
				| ContractExeException
				| UnLinkedBlockException
				| ValidateScheduleException
				| AccountResourceInsufficientException
				| TaposException
				| TooBigTransactionException
				| TooBigTransactionResultException
				| DupTransactionException
				| TransactionExpirationException
				| BadNumberBlockException
				| BadBlockException
				| NonCommonBlockException
				| ReceiptCheckErrException
				| VMIllegalException e) {
				throw new P2pException(TypeEnum.BAD_BLOCK, e);
			}
		}
	}

	public void pushTransaction(TransactionCapsule trx) throws P2pException {
		try {
			dbManager.pushTransaction(trx);
		} catch (ContractSizeNotEqualToOneException
			| VMIllegalException e) {
			throw new P2pException(TypeEnum.BAD_TRX, e);
		} catch (ContractValidateException
			| ValidateSignatureException
			| ContractExeException
			| DupTransactionException
			| TaposException
			| TooBigTransactionException
			| TransactionExpirationException
			| ReceiptCheckErrException
			| TooBigTransactionResultException
			| AccountResourceInsufficientException e) {
			throw new P2pException(TypeEnum.TRX_EXE_FAILED, e);
		}
	}

	public boolean validBlock(BlockCapsule block) throws P2pException {
		try {
			if (!block.validateSignature(dbManager)) {
				return false;
			}
			boolean flag = false;
			List<WitnessCapsule> witnesses = witnessStore.getAllWitnesses();
			for (WitnessCapsule witness : witnesses) {
				if (witness.getAddress().equals(block.getWitnessAddress())) {
					flag = true;
					break;
				}
			}
			return flag;
		} catch (ValidateSignatureException e) {
			throw new P2pException(TypeEnum.BAD_BLOCK, e);
		}
	}
}
