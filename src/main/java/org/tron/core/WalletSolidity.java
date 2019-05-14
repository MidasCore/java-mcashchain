package org.tron.core;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.TransactionList;
import org.tron.common.utils.ByteArray;
import org.tron.core.db.api.StoreAPI;
import org.tron.protos.Protocol.Transaction;

import java.util.List;

@Slf4j
@Component
public class WalletSolidity {

	@Autowired
	private StoreAPI storeAPI;

	public TransactionList getTransactionsFromThis(ByteString thisAddress, long offset, long limit) {
		List<Transaction> transactionsFromThis = storeAPI
				.getTransactionsFromThis(ByteArray.toHexString(thisAddress.toByteArray()), offset, limit);
		TransactionList transactionList = TransactionList.newBuilder()
				.addAllTransaction(transactionsFromThis).build();
		return transactionList;
	}

	public TransactionList getTransactionsToThis(ByteString toAddress, long offset, long limit) {
		List<Transaction> transactionsToThis = storeAPI
				.getTransactionsToThis(ByteArray.toHexString(toAddress.toByteArray()), offset, limit);
		TransactionList transactionList = TransactionList.newBuilder()
				.addAllTransaction(transactionsToThis).build();
		return transactionList;
	}
}
