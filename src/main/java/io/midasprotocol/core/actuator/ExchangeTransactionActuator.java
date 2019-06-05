package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ExchangeCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.protos.Contract.ExchangeTransactionContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "actuator")
public class ExchangeTransactionActuator extends AbstractActuator {

	ExchangeTransactionActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ExchangeTransactionContract exchangeTransactionContract = this.contract
				.unpack(ExchangeTransactionContract.class);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(exchangeTransactionContract.getOwnerAddress().toByteArray());

			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore().
				get(ByteArray.fromLong(exchangeTransactionContract.getExchangeId()));

			long firstTokenID = exchangeCapsule.getFirstTokenId();
			long secondTokenID = exchangeCapsule.getSecondTokenId();

			long tokenID = exchangeTransactionContract.getTokenId();
			long tokenQuant = exchangeTransactionContract.getQuant();

			long anotherTokenID;
			long anotherTokenQuant = exchangeCapsule.transaction(tokenID, tokenQuant);

			if (tokenID == firstTokenID) {
				anotherTokenID = secondTokenID;
			} else {
				anotherTokenID = firstTokenID;
			}

			long newBalance = accountCapsule.getBalance() - calcFee();
			accountCapsule.setBalance(newBalance);

			if (tokenID == 0) {
				accountCapsule.setBalance(newBalance - tokenQuant);
			} else {
				accountCapsule.reduceAssetAmountV2(tokenID, tokenQuant);
			}

			if (anotherTokenID == 0) {
				accountCapsule.setBalance(newBalance + anotherTokenQuant);
			} else {
				accountCapsule.addAssetAmountV2(anotherTokenID, anotherTokenQuant);
			}

			dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

			dbManager.putExchangeCapsule(exchangeCapsule);

			ret.setExchangeReceivedAmount(anotherTokenQuant);
			ret.setStatus(fee, code.SUCCESS);
		} catch (ItemNotFoundException | InvalidProtocolBufferException e) {
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
		if (!this.contract.is(ExchangeTransactionContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [ExchangeTransactionContract],real type[" + contract
					.getClass() + "]");
		}
		final ExchangeTransactionContract contract;
		try {
			contract = this.contract.unpack(ExchangeTransactionContract.class);
		} catch (InvalidProtocolBufferException e) {
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		if (!this.dbManager.getAccountStore().has(ownerAddress)) {
			throw new ContractValidateException("account[" + readableOwnerAddress + "] not exists");
		}

		AccountCapsule accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);

		if (accountCapsule.getBalance() < calcFee()) {
			throw new ContractValidateException("No enough balance for exchange transaction fee!");
		}

		ExchangeCapsule exchangeCapsule;
		try {
			exchangeCapsule = dbManager.getExchangeStore().
				get(ByteArray.fromLong(contract.getExchangeId()));
		} catch (ItemNotFoundException ex) {
			throw new ContractValidateException("Exchange[" + contract.getExchangeId() + "] not exists");
		}

		long firstTokenID = exchangeCapsule.getFirstTokenId();
		long secondTokenID = exchangeCapsule.getSecondTokenId();
		long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
		long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

		long tokenID = contract.getTokenId();
		long tokenQuant = contract.getQuant();
		long tokenExpected = contract.getExpected();

		if (tokenID != firstTokenID && tokenID != secondTokenID) {
			throw new ContractValidateException("token is not in exchange");
		}

		if (tokenQuant <= 0) {
			throw new ContractValidateException("token quant must greater than zero");
		}

		if (tokenExpected <= 0) {
			throw new ContractValidateException("token expected must greater than zero");
		}

		if (firstTokenBalance == 0 || secondTokenBalance == 0) {
			throw new ContractValidateException("Token balance in exchange is equal with 0,"
				+ "the exchange has been closed");
		}

		long balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
		long tokenBalance = tokenID == firstTokenID ? firstTokenBalance : secondTokenBalance;
		tokenBalance += tokenQuant;
		if (tokenBalance > balanceLimit) {
			throw new ContractValidateException("token balance must less than " + balanceLimit);
		}

		if (tokenID == 0) {
			if (accountCapsule.getBalance() < (tokenQuant + calcFee())) {
				throw new ContractValidateException("balance is not enough");
			}
		} else {
			if (!accountCapsule.assetBalanceEnoughV2(tokenID, tokenQuant)) {
				throw new ContractValidateException("token balance is not enough");
			}
		}

		long anotherTokenQuant = exchangeCapsule.transaction(tokenID, tokenQuant);
		if (anotherTokenQuant < tokenExpected) {
			throw new ContractValidateException("token required must greater than expected");
		}

		return true;
	}


	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ExchangeTransactionContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
