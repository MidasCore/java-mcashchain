package io.midasprotocol.core.net.message;

import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.protos.Protocol.Inventory;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;

import java.util.List;

public class FetchInvDataMessage extends InventoryMessage {


	public FetchInvDataMessage(byte[] packed) throws Exception {
		super(packed);
		this.type = MessageTypes.FETCH_INV_DATA.asByte();
	}

	public FetchInvDataMessage(Inventory inv) {
		super(inv);
		this.type = MessageTypes.FETCH_INV_DATA.asByte();
	}

	public FetchInvDataMessage(List<Sha256Hash> hashList, InventoryType type) {
		super(hashList, type);
		this.type = MessageTypes.FETCH_INV_DATA.asByte();
	}

}
