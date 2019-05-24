package io.midasprotocol.core.net.message;

import io.midasprotocol.protos.Protocol;

public class FetchBlockHeadersMessage extends InventoryMessage {

	public FetchBlockHeadersMessage(byte[] packed) throws Exception {
		super(packed);
		this.type = MessageTypes.FETCH_BLOCK_HEADERS.asByte();
	}

	public FetchBlockHeadersMessage(Protocol.Inventory inv) {
		super(inv);
		this.type = MessageTypes.FETCH_BLOCK_HEADERS.asByte();
	}

}