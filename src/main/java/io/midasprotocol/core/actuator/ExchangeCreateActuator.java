package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ExchangeCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.ExchangeCreateContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class ExchangeCreateActuator extends AbstractActuator {

	ExchangeCreateActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ExchangeCreateContract exchangeCreateContract = this.contract
					.unpack(ExchangeCreateContract.class);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
					.get(exchangeCreateContract.getOwnerAddress().toByteArray());

			long firstTokenID = exchangeCreateContract.getFirstTokenId();
			long secondTokenID = exchangeCreateContract.getSecondTokenId();
			long firstTokenBalance = exchangeCreateContract.getFirstTokenBalance();
			long secondTokenBalance = exchangeCreateContract.getSecondTokenBalance();

			long newBalance = accountCapsule.getBalance() - fee;

			accountCapsule.setBalance(newBalance);

			if (firstTokenID == 0) {
				accountCapsule.setBalance(newBalance - firstTokenBalance);
			} else {
				accountCapsule.reduceAssetAmountV2(firstTokenID, firstTokenBalance);
			}

			if (secondTokenID == 0) {
				accountCapsule.setBalance(newBalance - secondTokenBalance);
			} else {
				accountCapsule.reduceAssetAmountV2(secondTokenID, secondTokenBalance);
			}

			long id = dbManager.getDynamicPropertiesStore().getLatestExchangeNum() + 1;
			long now = dbManager.getHeadBlockTimeStamp();
			ExchangeCapsule exchangeCapsule =
					new ExchangeCapsule(
							exchangeCreateContract.getOwnerAddress(),
							id,
							now,
							firstTokenID,
							secondTokenID
					);
			exchangeCapsule.setBalance(firstTokenBalance, secondTokenBalance);
			dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

			dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
			dbManager.getDynamicPropertiesStore().saveLatestExchangeNum(id);

			dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().createDbKey(), fee);

			ret.setExchangeId(id);
			ret.setStatus(fee, code.SUCCESS);
		} catch (BalanceInsufficientException | InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
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
		if (!this.contract.is(ExchangeCreateContract.class)) {
			throw new ContractValidateException(
					"Contract type error, expected ExchangeCreateContract, actual " + contract.getClass());
		}
		final ExchangeCreateContract contract;
		try {
			contract = this.contract.unpack(ExchangeCreateContract.class);
		} catch (InvalidProtocolBufferException e) {
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		if (!this.dbManager.getAccountStore().has(ownerAddress)) {
			throw new ContractValidateException("Account " + readableOwnerAddress + " does not exist");
		}

		AccountCapsule accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);

		if (accountCapsule.getBalance() < calcFee()) {
			throw new ContractValidateException("Not enough balance for exchange create fee");
		}

		long firstTokenID = contract.getFirstTokenId();
		long secondTokenID = contract.getSecondTokenId();
		long firstTokenBalance = contract.getFirstTokenBalance();
		long secondTokenBalance = contract.getSecondTokenBalance();

		if (firstTokenID == secondTokenID) {
			throw new ContractValidateException("Cannot exchange same tokens");
		}

		if (firstTokenBalance <= 0 || secondTokenBalance <= 0) {
			throw new ContractValidateException("Token balance must greater than zero");
		}

		long balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
		if (firstTokenBalance > balanceLimit || secondTokenBalance > balanceLimit) {
			throw new ContractValidateException("Token balance must less than " + balanceLimit);
		}

		if (firstTokenID == 0) {
			if (accountCapsule.getBalance() < (firstTokenBalance + calcFee())) {
				throw new ContractValidateException("Balance is not enough");
			}
		} else {
			if (!accountCapsule.assetBalanceEnoughV2(firstTokenID, firstTokenBalance)) {
				throw new ContractValidateException("First token balance is not enough");
			}
		}

		if (secondTokenID == 0) {
			if (accountCapsule.getBalance() < (secondTokenBalance + calcFee())) {
				throw new ContractValidateException("Balance is not enough");
			}
		} else {
			if (!accountCapsule.assetBalanceEnoughV2(secondTokenID, secondTokenBalance)) {
				throw new ContractValidateException("Second token balance is not enough");
			}
		}

		return true;
	}


	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ExchangeCreateContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return dbManager.getDynamicPropertiesStore().getExchangeCreateFee();
	}

}
