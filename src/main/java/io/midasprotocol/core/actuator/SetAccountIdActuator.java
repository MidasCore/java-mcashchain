package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.utils.TransactionUtil;
import io.midasprotocol.core.db.AccountIdIndexStore;
import io.midasprotocol.core.db.AccountStore;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.SetAccountIdContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "actuator")
public class SetAccountIdActuator extends AbstractActuator {

	SetAccountIdActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		final SetAccountIdContract setAccountIdContract;
		final long fee = calcFee();
		try {
			setAccountIdContract = contract.unpack(SetAccountIdContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		}

		byte[] ownerAddress = setAccountIdContract.getOwnerAddress().toByteArray();
		AccountStore accountStore = dbManager.getAccountStore();
		AccountIdIndexStore accountIdIndexStore = dbManager.getAccountIdIndexStore();
		AccountCapsule account = accountStore.get(ownerAddress);

		account.setAccountId(setAccountIdContract.getAccountId().toByteArray());
		accountStore.put(ownerAddress, account);
		accountIdIndexStore.put(account);
		ret.setStatus(fee, Code.SUCCESS);

		return true;
	}

	@Override
	public boolean validate() throws ContractValidateException {
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (this.dbManager == null) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(SetAccountIdContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [SetAccountIdContract],real type[" + contract
					.getClass() + "]");
		}
		final SetAccountIdContract setAccountIdContract;
		try {
			setAccountIdContract = contract.unpack(SetAccountIdContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = setAccountIdContract.getOwnerAddress().toByteArray();
		byte[] accountId = setAccountIdContract.getAccountId().toByteArray();
		if (!TransactionUtil.validAccountId(accountId)) {
			throw new ContractValidateException("Invalid accountId");
		}
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}

		AccountCapsule account = dbManager.getAccountStore().get(ownerAddress);
		if (account == null) {
			throw new ContractValidateException("Account has not existed");
		}
		if (account.getAccountId() != null && !account.getAccountId().isEmpty()) {
			throw new ContractValidateException("This account id already set");
		}
		if (dbManager.getAccountIdIndexStore().has(accountId)) {
			throw new ContractValidateException("This id has existed");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(SetAccountIdContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
