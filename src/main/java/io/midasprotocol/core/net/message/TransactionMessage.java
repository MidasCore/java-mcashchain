package io.midasprotocol.core.net.message;

import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.overlay.message.Message;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.core.exception.P2pException;
import io.midasprotocol.protos.Protocol.Transaction;

public class TransactionMessage extends TronMessage {

	private TransactionCapsule transactionCapsule;

	public TransactionMessage(byte[] data) throws BadItemException, P2pException, InvalidProtocolBufferException {
		super(data);
		this.transactionCapsule = new TransactionCapsule(getCodedInputStream(data));
		this.type = MessageTypes.TRX.asByte();
		if (Message.isFilter()) {
			compareBytes(data, transactionCapsule.getInstance().toByteArray());
			TransactionCapsule.validContractProto(transactionCapsule.getInstance().getRawData().getContract(0));
		}
	}

	public TransactionMessage(Transaction trx) {
		this.transactionCapsule = new TransactionCapsule(trx);
		this.type = MessageTypes.TRX.asByte();
		this.data = trx.toByteArray();
	}

	@Override
	public String toString() {
		return new StringBuilder().append(super.toString())
			.append("messageId: ").append(super.getMessageId()).toString();
	}

	@Override
	public Sha256Hash getMessageId() {
		return this.transactionCapsule.getTransactionId();
	}

	@Override
	public Class<?> getAnswerMessage() {
		return null;
	}

	public TransactionCapsule getTransactionCapsule() {
		return this.transactionCapsule;
	}
}
