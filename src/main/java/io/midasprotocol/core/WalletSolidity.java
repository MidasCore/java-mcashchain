package io.midasprotocol.core;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.midasprotocol.api.GrpcAPI.TransactionList;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.db.api.StoreAPI;
import io.midasprotocol.protos.Protocol.Transaction;

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
