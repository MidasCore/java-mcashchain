package io.midasprotocol.core.net.services;

import org.junit.Assert;
import org.junit.Test;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.net.message.BlockMessage;
import io.midasprotocol.core.net.peer.Item;
import io.midasprotocol.core.net.service.AdvService;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;

public class AdvServiceTest {

	private AdvService service = new AdvService();

	@Test
	public void testAddInv() {
		boolean flag;
		Item item = new Item(Sha256Hash.ZERO_HASH, InventoryType.BLOCK);
		flag = service.addInv(item);
		Assert.assertTrue(flag);
		flag = service.addInv(item);
		Assert.assertFalse(flag);
	}

	@Test
	public void testBroadcast() {
		BlockCapsule blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
			System.currentTimeMillis(), Sha256Hash.ZERO_HASH.getByteString());
		BlockMessage msg = new BlockMessage(blockCapsule);
		service.broadcast(msg);
		Item item = new Item(blockCapsule.getBlockId(), InventoryType.BLOCK);
		Assert.assertNotNull(service.getMessage(item));
	}
}
