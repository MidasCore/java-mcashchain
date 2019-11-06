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
import io.midasprotocol.protos.Contract.ExchangeInjectContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;

@Slf4j(topic = "actuator")
public class ExchangeInjectActuator extends AbstractActuator {

	ExchangeInjectActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ExchangeInjectContract exchangeInjectContract = this.contract
				.unpack(ExchangeInjectContract.class);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(exchangeInjectContract.getOwnerAddress().toByteArray());

			ExchangeCapsule exchangeCapsule;
			exchangeCapsule = dbManager.getExchangeStore().
				get(ByteArray.fromLong(exchangeInjectContract.getExchangeId()));

			long firstTokenID = exchangeCapsule.getFirstTokenId();
			long secondTokenID = exchangeCapsule.getSecondTokenId();
			long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
			long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

			long tokenID = exchangeInjectContract.getTokenId();
			long tokenQuant = exchangeInjectContract.getQuant();

			long anotherTokenID;
			long anotherTokenQuant;

			if (tokenID == firstTokenID) {
				anotherTokenID = secondTokenID;
				anotherTokenQuant = Math
					.floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
				exchangeCapsule.setBalance(firstTokenBalance + tokenQuant,
					secondTokenBalance + anotherTokenQuant);
			} else {
				anotherTokenID = firstTokenID;
				anotherTokenQuant = Math
					.floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
				exchangeCapsule.setBalance(firstTokenBalance + anotherTokenQuant,
					secondTokenBalance + tokenQuant);
			}

			long newBalance = accountCapsule.getBalance() - calcFee();
			accountCapsule.setBalance(newBalance);

			if (tokenID == 0) {
				accountCapsule.setBalance(newBalance - tokenQuant);
			} else {
				accountCapsule.reduceAssetAmount(tokenID, tokenQuant);
			}

			if (anotherTokenID == 0) {
				accountCapsule.setBalance(newBalance - anotherTokenQuant);
			} else {
				accountCapsule.reduceAssetAmount(anotherTokenID, anotherTokenQuant);
			}
			dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

			dbManager.putExchangeCapsule(exchangeCapsule);

			ret.setExchangeInjectAnotherAmount(anotherTokenQuant);
			ret.setStatus(fee, Code.SUCCESS);
		} catch (ItemNotFoundException | InvalidProtocolBufferException e) {
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
		if (!this.contract.is(ExchangeInjectContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [ExchangeInjectContract],real type[" + contract
					.getClass() + "]");
		}
		final ExchangeInjectContract contract;
		try {
			contract = this.contract.unpack(ExchangeInjectContract.class);
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
			throw new ContractValidateException("No enough balance for exchange inject fee!");
		}

		ExchangeCapsule exchangeCapsule;
		try {
			exchangeCapsule = dbManager.getExchangeStore().
				get(ByteArray.fromLong(contract.getExchangeId()));

		} catch (ItemNotFoundException ex) {
			throw new ContractValidateException("Exchange[" + contract.getExchangeId() + "] not exists");
		}

		if (!accountCapsule.getAddress().equals(exchangeCapsule.getCreatorAddress())) {
			throw new ContractValidateException("account[" + readableOwnerAddress + "] is not creator");
		}

		long firstTokenID = exchangeCapsule.getFirstTokenId();
		long secondTokenID = exchangeCapsule.getSecondTokenId();
		long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
		long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

		long tokenID = contract.getTokenId();
		long tokenQuant = contract.getQuant();

		long anotherTokenID;
		long anotherTokenQuant;

		if (tokenID != firstTokenID && tokenID != secondTokenID) {
			throw new ContractValidateException("token id is not in exchange");
		}

		if (firstTokenBalance == 0 || secondTokenBalance == 0) {
			throw new ContractValidateException("Token balance in exchange is equal with 0, "
				+ "the exchange has been closed");
		}

		if (tokenQuant <= 0) {
			throw new ContractValidateException("injected token quant must greater than zero");
		}

		BigInteger bigFirstTokenBalance = new BigInteger(String.valueOf(firstTokenBalance));
		BigInteger bigSecondTokenBalance = new BigInteger(String.valueOf(secondTokenBalance));
		BigInteger bigTokenQuant = new BigInteger(String.valueOf(tokenQuant));
		long newTokenBalance, newAnotherTokenBalance;
		if (tokenID == firstTokenID) {
			anotherTokenID = secondTokenID;
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
			anotherTokenQuant = bigSecondTokenBalance.multiply(bigTokenQuant)
				.divide(bigFirstTokenBalance).longValueExact();
			newTokenBalance = firstTokenBalance + tokenQuant;
			newAnotherTokenBalance = secondTokenBalance + anotherTokenQuant;
		} else {
			anotherTokenID = firstTokenID;
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
			anotherTokenQuant = bigFirstTokenBalance.multiply(bigTokenQuant)
				.divide(bigSecondTokenBalance).longValueExact();
			newTokenBalance = secondTokenBalance + tokenQuant;
			newAnotherTokenBalance = firstTokenBalance + anotherTokenQuant;
		}

		if (anotherTokenQuant <= 0) {
			throw new ContractValidateException("the calculated token quant must be greater than 0");
		}

		long balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
		if (newTokenBalance > balanceLimit || newAnotherTokenBalance > balanceLimit) {
			throw new ContractValidateException("token balance must less than " + balanceLimit);
		}

		if (tokenID == 0) {
			if (accountCapsule.getBalance() < (tokenQuant + calcFee())) {
				throw new ContractValidateException("balance is not enough");
			}
		} else {
			if (!accountCapsule.assetBalanceEnough(tokenID, tokenQuant)) {
				throw new ContractValidateException("token balance is not enough");
			}
		}

		if (anotherTokenID == 0) {
			if (accountCapsule.getBalance() < (anotherTokenQuant + calcFee())) {
				throw new ContractValidateException("balance is not enough");
			}
		} else {
			if (!accountCapsule.assetBalanceEnough(anotherTokenID, anotherTokenQuant)) {
				throw new ContractValidateException("another token balance is not enough");
			}
		}

		return true;
	}


	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ExchangeInjectContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
