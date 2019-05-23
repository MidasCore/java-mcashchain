package io.midasprotocol.core.db;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.midasprotocol.core.capsule.TransactionInfoCapsule;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.BadItemException;

@Component
public class TransactionHistoryStore extends TronStoreWithRevoking<TransactionInfoCapsule> {

	@Autowired
	public TransactionHistoryStore(@Value("transactionHistoryStore") String dbName) {
		super(dbName);
	}

	@Override
	public TransactionInfoCapsule get(byte[] key) throws BadItemException {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new TransactionInfoCapsule(value);
	}

	@Override
	public void put(byte[] key, TransactionInfoCapsule item) {
		if (BooleanUtils.toBoolean(Args.getInstance().getStorage().getTransactionHistorySwitch())) {
			super.put(key, item);
		}
	}
}