package org.tron.core.net.messagehandler;

import javafx.util.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.tron.core.capsule.BlockCapsule.BlockId;
import org.tron.core.config.Parameter.NodeConstant;
import org.tron.core.exception.P2pException;
import org.tron.core.net.message.ChainInventoryMessage;
import org.tron.core.net.peer.PeerConnection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ChainInventoryMsgHandlerTest {

	private ChainInventoryMsgHandler handler = new ChainInventoryMsgHandler();
	private PeerConnection peer = new PeerConnection();
	private ChainInventoryMessage msg = new ChainInventoryMessage(new ArrayList<>(), 0L);
	private List<BlockId> blockIds = new ArrayList<>();

	@Test
	public void testProcessMessage() {
		try {
			handler.processMessage(peer, msg);
		} catch (P2pException e) {
			Assert.assertEquals("not send syncBlockChainMsg", e.getMessage());
		}

		peer.setSyncChainRequested(new Pair<>(new LinkedList<>(), System.currentTimeMillis()));

		try {
			handler.processMessage(peer, msg);
		} catch (P2pException e) {
			Assert.assertEquals("blockIds is empty", e.getMessage());
		}

		long size = NodeConstant.SYNC_FETCH_BATCH_NUM + 2;
		for (int i = 0; i < size; i++) {
			blockIds.add(new BlockId());
		}
		msg = new ChainInventoryMessage(blockIds, 0L);

		try {
			handler.processMessage(peer, msg);
		} catch (P2pException e) {
			Assert.assertEquals(e.getMessage(), "big blockIds size: " + size);
		}

		blockIds.clear();
		size = NodeConstant.SYNC_FETCH_BATCH_NUM / 100;
		for (int i = 0; i < size; i++) {
			blockIds.add(new BlockId());
		}
		msg = new ChainInventoryMessage(blockIds, 100L);

		try {
			handler.processMessage(peer, msg);
		} catch (P2pException e) {
			Assert.assertEquals(e.getMessage(), "remain: 100, blockIds size: " + size);
		}
	}

}
