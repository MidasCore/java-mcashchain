package io.midasprotocol.core.net.message;

import io.midasprotocol.protos.Protocol.Block;
import io.midasprotocol.protos.Protocol.Items;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public class BlocksMessage extends TronMessage {

	private List<Block> blocks;

	public BlocksMessage(byte[] data) throws Exception {
		this.type = MessageTypes.BLOCKS.asByte();
		this.data = data;
		Items items = Items.parseFrom(data);
		if (items.getType() == Items.ItemType.BLOCK) {
			blocks = items.getBlocksList();
		}
	}

	public List<Block> getBlocks() {
		return blocks;
	}

	@Override
	public String toString() {
		return super.toString() + "size: " + (CollectionUtils.isNotEmpty(blocks) ? blocks
			.size() : 0);
	}

	@Override
	public Class<?> getAnswerMessage() {
		return null;
	}

}
