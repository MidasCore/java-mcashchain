package io.midasprotocol.core.net.message;

import com.google.protobuf.DiscardUnknownFieldsParser;
import com.google.protobuf.Parser;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction;

import java.util.List;

public class TransactionsMessage extends TronMessage {

	private Protocol.Transactions transactions;

	public TransactionsMessage(List<Transaction> trxs) {
		Protocol.Transactions.Builder builder = Protocol.Transactions.newBuilder();
		trxs.forEach(builder::addTransactions);
		this.transactions = builder.build();
		this.type = MessageTypes.TRXS.asByte();
		this.data = this.transactions.toByteArray();
	}

	public TransactionsMessage(byte[] data) throws Exception {
		super(data);
		this.type = MessageTypes.TRXS.asByte();
		this.transactions = Protocol.Transactions.parseFrom(getCodedInputStream(data));
		if (isFilter()) {
			compareBytes(data, transactions.toByteArray());
			TransactionCapsule.validContractProto(transactions.getTransactionsList());
		}
	}

	public Protocol.Transactions getTransactions() {
		return transactions;
	}

	@Override
	public String toString() {
		return new StringBuilder().append(super.toString()).append("trx size: ")
			.append(this.transactions.getTransactionsList().size()).toString();
	}

	@Override
	public Class<?> getAnswerMessage() {
		return null;
	}

}
