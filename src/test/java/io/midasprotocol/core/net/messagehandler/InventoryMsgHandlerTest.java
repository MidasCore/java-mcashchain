package io.midasprotocol.core.net.messagehandler;

import org.junit.Test;
import io.midasprotocol.core.net.message.InventoryMessage;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;

import java.util.ArrayList;

public class InventoryMsgHandlerTest {

	private InventoryMsgHandler handler = new InventoryMsgHandler();
	private PeerConnection peer = new PeerConnection();

	@Test
	public void testProcessMessage() {
		InventoryMessage msg = new InventoryMessage(new ArrayList<>(), InventoryType.TRX);

		peer.setNeedSyncFromPeer(true);
		peer.setNeedSyncFromUs(true);
		handler.processMessage(peer, msg);

		peer.setNeedSyncFromPeer(true);
		peer.setNeedSyncFromUs(false);
		handler.processMessage(peer, msg);

		peer.setNeedSyncFromPeer(false);
		peer.setNeedSyncFromUs(true);
		handler.processMessage(peer, msg);

	}
}
