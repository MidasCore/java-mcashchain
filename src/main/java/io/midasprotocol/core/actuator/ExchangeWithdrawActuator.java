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
import io.midasprotocol.protos.Contract.ExchangeWithdrawContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.BigInteger;

@Slf4j(topic = "actuator")
public class ExchangeWithdrawActuator extends AbstractActuator {

	ExchangeWithdrawActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ExchangeWithdrawContract exchangeWithdrawContract = this.contract
				.unpack(ExchangeWithdrawContract.class);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(exchangeWithdrawContract.getOwnerAddress().toByteArray());

			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore().
				get(ByteArray.fromLong(exchangeWithdrawContract.getExchangeId()));

			long firstTokenID = exchangeCapsule.getFirstTokenId();
			long secondTokenID = exchangeCapsule.getSecondTokenId();
			long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
			long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

			long tokenID = exchangeWithdrawContract.getTokenId();
			long tokenQuant = exchangeWithdrawContract.getQuant();

			long anotherTokenID;
			long anotherTokenQuant;

			BigInteger bigFirstTokenBalance = new BigInteger(String.valueOf(firstTokenBalance));
			BigInteger bigSecondTokenBalance = new BigInteger(String.valueOf(secondTokenBalance));
			BigInteger bigTokenQuant = new BigInteger(String.valueOf(tokenQuant));
			if (tokenID == firstTokenID) {
				anotherTokenID = secondTokenID;
				anotherTokenQuant = bigSecondTokenBalance.multiply(bigTokenQuant)
					.divide(bigFirstTokenBalance).longValueExact();
				exchangeCapsule.setBalance(firstTokenBalance - tokenQuant,
					secondTokenBalance - anotherTokenQuant);
			} else {
				anotherTokenID = firstTokenID;
				anotherTokenQuant = bigFirstTokenBalance.multiply(bigTokenQuant)
					.divide(bigSecondTokenBalance).longValueExact();
				exchangeCapsule.setBalance(firstTokenBalance - anotherTokenQuant,
					secondTokenBalance - tokenQuant);
			}

			long newBalance = accountCapsule.getBalance() - calcFee();

			if (tokenID == 0) {
				accountCapsule.setBalance(newBalance + tokenQuant);
			} else {
				accountCapsule.addAssetAmount(tokenID, tokenQuant);
			}

			if (anotherTokenID == 0) {
				accountCapsule.setBalance(newBalance + anotherTokenQuant);
			} else {
				accountCapsule.addAssetAmount(anotherTokenID, anotherTokenQuant);
			}

			dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

			dbManager.putExchangeCapsule(exchangeCapsule);

			ret.setExchangeWithdrawAnotherAmount(anotherTokenQuant);
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
		if (!this.contract.is(ExchangeWithdrawContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [ExchangeWithdrawContract],real type[" + contract
					.getClass() + "]");
		}
		final ExchangeWithdrawContract contract;
		try {
			contract = this.contract.unpack(ExchangeWithdrawContract.class);
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
			throw new ContractValidateException("No enough balance for exchange withdraw fee!");
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

		long anotherTokenQuant;

		if (tokenID != firstTokenID && tokenID != secondTokenID) {
			throw new ContractValidateException("token is not in exchange");
		}

		if (tokenQuant <= 0) {
			throw new ContractValidateException("withdraw token quant must greater than zero");
		}

		if (firstTokenBalance == 0 || secondTokenBalance == 0) {
			throw new ContractValidateException("Token balance in exchange is equal with 0,"
				+ "the exchange has been closed");
		}

		BigDecimal bigFirstTokenBalance = new BigDecimal(String.valueOf(firstTokenBalance));
		BigDecimal bigSecondTokenBalance = new BigDecimal(String.valueOf(secondTokenBalance));
		BigDecimal bigTokenQuant = new BigDecimal(String.valueOf(tokenQuant));
		if (tokenID == firstTokenID) {
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
			anotherTokenQuant = bigSecondTokenBalance.multiply(bigTokenQuant)
				.divideToIntegralValue(bigFirstTokenBalance).longValueExact();
			if (firstTokenBalance < tokenQuant || secondTokenBalance < anotherTokenQuant) {
				throw new ContractValidateException("exchange balance is not enough");
			}

			if (anotherTokenQuant <= 0) {
				throw new ContractValidateException("withdraw another token quant must greater than zero");
			}

			double remainder = bigSecondTokenBalance.multiply(bigTokenQuant)
				.divide(bigFirstTokenBalance, 4, BigDecimal.ROUND_HALF_UP).doubleValue()
				- anotherTokenQuant;
			if (remainder / anotherTokenQuant > 0.0001) {
				throw new ContractValidateException("Not precise enough");
			}

		} else {
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
			anotherTokenQuant = bigFirstTokenBalance.multiply(bigTokenQuant)
				.divideToIntegralValue(bigSecondTokenBalance).longValueExact();
			if (secondTokenBalance < tokenQuant || firstTokenBalance < anotherTokenQuant) {
				throw new ContractValidateException("exchange balance is not enough");
			}

			if (anotherTokenQuant <= 0) {
				throw new ContractValidateException("withdraw another token quant must greater than zero");
			}

			double remainder = bigFirstTokenBalance.multiply(bigTokenQuant)
				.divide(bigSecondTokenBalance, 4, BigDecimal.ROUND_HALF_UP).doubleValue()
				- anotherTokenQuant;
			if (remainder / anotherTokenQuant > 0.0001) {
				throw new ContractValidateException("Not precise enough");
			}
		}

		return true;
	}


	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ExchangeWithdrawContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
