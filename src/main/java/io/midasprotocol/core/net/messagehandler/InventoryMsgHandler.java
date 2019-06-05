package io.midasprotocol.core.net.messagehandler;

import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.net.TronNetDelegate;
import io.midasprotocol.core.net.message.InventoryMessage;
import io.midasprotocol.core.net.message.TronMessage;
import io.midasprotocol.core.net.peer.Item;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.core.net.service.AdvService;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InventoryMsgHandler implements TronMsgHandler {

	@Autowired
	private TronNetDelegate tronNetDelegate;

	@Autowired
	private AdvService advService;

	@Autowired
	private TransactionsMsgHandler transactionsMsgHandler;

	private int maxCountIn10s = 10_000;

	private boolean fastForward = Args.getInstance().isFastForward();

	@Override
	public void processMessage(PeerConnection peer, TronMessage msg) {
		InventoryMessage inventoryMessage = (InventoryMessage) msg;
		InventoryType type = inventoryMessage.getInventoryType();

		if (fastForward && inventoryMessage.getInventoryType().equals(InventoryType.TRX)) {
			return;
		}

		if (!check(peer, inventoryMessage)) {
			return;
		}

		for (Sha256Hash id : inventoryMessage.getHashList()) {
			Item item = new Item(id, type);
			peer.getAdvInvReceive().put(item, System.currentTimeMillis());
			advService.addInv(item);
		}
	}

	private boolean check(PeerConnection peer, InventoryMessage inventoryMessage) {
		InventoryType type = inventoryMessage.getInventoryType();
		int size = inventoryMessage.getHashList().size();

//    if (size > NetConstants.MAX_INV_FETCH_PER_PEER) {
//      throw new P2pException(TypeEnum.BAD_MESSAGE, "size: " + size);
//    }

		if (peer.isNeedSyncFromPeer() || peer.isNeedSyncFromUs()) {
			logger.warn("Drop inv: {} size: {} from Peer {}, syncFromUs: {}, syncFromPeer: {}.",
				type, size, peer.getInetAddress(), peer.isNeedSyncFromUs(), peer.isNeedSyncFromPeer());
			return false;
		}

		if (type.equals(InventoryType.TRX)) {
			int count = peer.getNodeStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);
			if (count > maxCountIn10s) {
				logger.warn("Drop inv: {} size: {} from Peer {}, Inv count: {} is overload.",
					type, size, peer.getInetAddress(), count);
				return false;
			}

			if (transactionsMsgHandler.isBusy()) {
				logger.warn("Drop inv: {} size: {} from Peer {}, transactionsMsgHandler is busy.",
					type, size, peer.getInetAddress());
				return false;
			}
		}

		return true;
	}
}
