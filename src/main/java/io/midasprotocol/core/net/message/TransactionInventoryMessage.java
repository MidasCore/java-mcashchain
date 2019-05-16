package io.midasprotocol.core.net.message;

import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.protos.Protocol.Inventory;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;

import java.util.List;

public class TransactionInventoryMessage extends InventoryMessage {

	public TransactionInventoryMessage(byte[] packed) throws Exception {
		super(packed);
	}

	public TransactionInventoryMessage(Inventory inv) {
		super(inv);
	}

	public TransactionInventoryMessage(List<Sha256Hash> hashList) {
		super(hashList, InventoryType.TRX);
	}
}
