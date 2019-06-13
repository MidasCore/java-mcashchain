package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.AccountCreateContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "actuator")
public class CreateAccountActuator extends AbstractActuator {

	CreateAccountActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret)
		throws ContractExeException {
		long fee = calcFee();
		try {
			AccountCreateContract accountCreateContract = contract.unpack(AccountCreateContract.class);
			boolean withDefaultPermission =
				dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
			AccountCapsule accountCapsule = new AccountCapsule(accountCreateContract,
				dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);

			dbManager.getAccountStore()
				.put(accountCreateContract.getAccountAddress().toByteArray(), accountCapsule);

			dbManager.adjustBalance(accountCreateContract.getOwnerAddress().toByteArray(), -fee);
			// Add to blackhole address
			dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().createDbKey(), fee);

			ret.setStatus(fee, Code.SUCCESS);
		} catch (BalanceInsufficientException | InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		}

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
		if (!contract.is(AccountCreateContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [AccountCreateContract],real type[" + contract
					.getClass() + "]");
		}
		final AccountCreateContract contract;
		try {
			contract = this.contract.unpack(AccountCreateContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
//    if (contract.getAccountName().isEmpty()) {
//      throw new ContractValidateException("AccountName is null");
//    }
		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
				"Account [" + readableOwnerAddress + "] not exists");
		}

		final long fee = calcFee();
		if (accountCapsule.getBalance() < fee) {
			throw new ContractValidateException(
				"Validate CreateAccountActuator error, insufficient fee.");
		}

		byte[] accountAddress = contract.getAccountAddress().toByteArray();
		if (!Wallet.addressValid(accountAddress)) {
			throw new ContractValidateException("Invalid account address");
		}

//    if (contract.getType() == null) {
//      throw new ContractValidateException("Type is null");
//    }

		if (dbManager.getAccountStore().has(accountAddress)) {
			throw new ContractValidateException("Account has existed");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(AccountCreateContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
	}
}
