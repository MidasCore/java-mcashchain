package io.midasprotocol.core.net.messagehandler;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.testng.collections.Lists;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.exception.P2pException;
import io.midasprotocol.core.net.message.BlockMessage;
import io.midasprotocol.core.net.peer.Item;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;
import io.midasprotocol.protos.Protocol.Transaction;

import java.util.List;

@Slf4j
public class BlockMsgHandlerTest {

	private BlockMsgHandler handler = new BlockMsgHandler();
	private PeerConnection peer = new PeerConnection();
	private BlockCapsule blockCapsule;
	private BlockMessage msg;

	@Test
	public void testProcessMessage() {
		try {
			blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
				System.currentTimeMillis(), Sha256Hash.ZERO_HASH.getByteString());
			msg = new BlockMessage(blockCapsule);
			handler.processMessage(peer, new BlockMessage(blockCapsule));
		} catch (P2pException e) {
			Assert.assertEquals("no request", e.getMessage());
		}

		try {
			List<Transaction> transactionList = Lists.newArrayList();
			for (int i = 0; i < 1100000; i++) {
				transactionList.add(Transaction.newBuilder().build());
			}
			blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH.getByteString(),
				System.currentTimeMillis() + 10000, transactionList);
			msg = new BlockMessage(blockCapsule);
			System.out.println("len = " + blockCapsule.getInstance().getSerializedSize());
			peer.getAdvInvRequest()
				.put(new Item(msg.getBlockId(), InventoryType.BLOCK), System.currentTimeMillis());
			handler.processMessage(peer, msg);
		} catch (P2pException e) {
			logger.info(e.toString());
			Assert.assertEquals("block size over limit", e.getMessage());
		}

		try {
			blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
				System.currentTimeMillis() + 10000, Sha256Hash.ZERO_HASH.getByteString());
			msg = new BlockMessage(blockCapsule);
			peer.getAdvInvRequest()
				.put(new Item(msg.getBlockId(), InventoryType.BLOCK), System.currentTimeMillis());
			handler.processMessage(peer, msg);
		} catch (P2pException e) {
			logger.info(e.toString());
			Assert.assertEquals("block time error", e.getMessage());
		}
	}

}
