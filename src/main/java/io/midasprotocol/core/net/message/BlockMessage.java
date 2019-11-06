package io.midasprotocol.core.net.message;

import io.midasprotocol.common.overlay.message.Message;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.capsule.BlockCapsule.BlockId;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.core.exception.P2pException;

public class BlockMessage extends TronMessage {

	private BlockCapsule block;

	public BlockMessage(byte[] data) throws BadItemException, P2pException {
		super(data);
		this.type = MessageTypes.BLOCK.asByte();
		this.block = new BlockCapsule(getCodedInputStream(data));
		if (Message.isFilter()) {
			Message.compareBytes(data, block.getInstance().toByteArray());
			TransactionCapsule.validContractProto(block.getInstance().getTransactionsList());
		}
	}

	public BlockMessage(BlockCapsule block) {
		data = block.getData();
		this.type = MessageTypes.BLOCK.asByte();
		this.block = block;
	}

	public BlockId getBlockId() {
		return getBlockCapsule().getBlockId();
	}

	public BlockCapsule getBlockCapsule() {
		return block;
	}

	@Override
	public Class<?> getAnswerMessage() {
		return null;
	}

	@Override
	public Sha256Hash getMessageId() {
		return getBlockCapsule().getBlockId();
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public String toString() {
		return new StringBuilder().append(super.toString()).append(block.getBlockId().getString())
			.append(", trx size: ").append(block.getTransactions().size()).append("\n").toString();
	}
}
