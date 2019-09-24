package io.midasprotocol.core.net.message;

import io.midasprotocol.core.capsule.BlockCapsule.BlockId;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.BlockInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BlockInventoryMessage extends TronMessage {

	protected BlockInventory blockInventory;

	public BlockInventoryMessage(byte[] data) throws Exception {
		this.type = MessageTypes.BLOCK_INVENTORY.asByte();
		this.blockInventory = Protocol.BlockInventory.parseFrom(data);
		this.data = this.blockInventory.toByteArray();
	}

	public BlockInventoryMessage(List<BlockId> blockIds, BlockInventory.Type type) {
		BlockInventory.Builder invBuilder = BlockInventory.newBuilder();
		blockIds.forEach(blockId -> {
			BlockInventory.BlockId.Builder b = BlockInventory.BlockId.newBuilder();
			b.setHash(blockId.getByteString());
			b.setNumber(blockId.getNum());
			invBuilder.addIds(b);
		});

		invBuilder.setType(type);
		blockInventory = invBuilder.build();
		this.type = MessageTypes.BLOCK_INVENTORY.asByte();
		this.data = blockInventory.toByteArray();
	}

	@Override
	public Class<?> getAnswerMessage() {
		return null;
	}

	private BlockInventory getBlockInventory() {
		return blockInventory;
	}

	public List<BlockId> getBlockIds() {
		return getBlockInventory().getIdsList().stream()
			.map(blockId -> new BlockId(blockId.getHash(), blockId.getNumber()))
			.collect(Collectors.toCollection(ArrayList::new));
	}

}
