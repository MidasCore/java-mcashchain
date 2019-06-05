package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.Parameter.ChainConstant;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.TransferContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class TransferActuator extends AbstractActuator {

	TransferActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	public static boolean validateForSmartContract(Deposit deposit, byte[] ownerAddress,
												   byte[] toAddress, long amount) throws ContractValidateException {
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}
		if (!Wallet.addressValid(toAddress)) {
			throw new ContractValidateException("Invalid toAddress");
		}

		if (Arrays.equals(toAddress, ownerAddress)) {
			throw new ContractValidateException("Cannot transfer trx to yourself.");
		}

		AccountCapsule ownerAccount = deposit.getAccount(ownerAddress);
		if (ownerAccount == null) {
			throw new ContractValidateException("Validate InternalTransfer error, no OwnerAccount.");
		}

		AccountCapsule toAccount = deposit.getAccount(toAddress);
		if (toAccount == null) {
			throw new ContractValidateException(
				"Validate InternalTransfer error, no ToAccount. And not allowed to create account in smart contract.");
		}

		long balance = ownerAccount.getBalance();

		if (amount < 0) {
			throw new ContractValidateException("Amount must greater than or equals 0.");
		}

		try {
			if (balance < amount) {
				throw new ContractValidateException(
					"Validate InternalTransfer error, balance is not sufficient.");
			}

			if (toAccount != null) {
				long toAddressBalance = Math.addExact(toAccount.getBalance(), amount);
			}
		} catch (ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		return true;
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			TransferContract transferContract = contract.unpack(TransferContract.class);
			long amount = transferContract.getAmount();
			byte[] toAddress = transferContract.getToAddress().toByteArray();
			byte[] ownerAddress = transferContract.getOwnerAddress().toByteArray();

			// if account with to_address does not exist, create it first.
			AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
			if (toAccount == null) {
				boolean withDefaultPermission =
					dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
				toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal,
					dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
				dbManager.getAccountStore().put(toAddress, toAccount);

				fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
			}
			dbManager.adjustBalance(ownerAddress, -fee);
			dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().createDbKey(), fee);
			ret.setStatus(fee, code.SUCCESS);
			dbManager.adjustBalance(ownerAddress, -amount);
			dbManager.adjustBalance(toAddress, amount);
		} catch (BalanceInsufficientException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		} catch (ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		} catch (InvalidProtocolBufferException e) {
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
		if (!this.contract.is(TransferContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [TransferContract],real type[" + contract
					.getClass() + "]");
		}
		long fee = calcFee();
		final TransferContract transferContract;
		try {
			transferContract = contract.unpack(TransferContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		byte[] toAddress = transferContract.getToAddress().toByteArray();
		byte[] ownerAddress = transferContract.getOwnerAddress().toByteArray();
		long amount = transferContract.getAmount();

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}
		if (!Wallet.addressValid(toAddress)) {
			throw new ContractValidateException("Invalid toAddress");
		}

		if (Arrays.equals(toAddress, ownerAddress)) {
			throw new ContractValidateException("Cannot transfer trx to yourself.");
		}

		AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
		if (ownerAccount == null) {
			throw new ContractValidateException("Validate TransferContract error, no OwnerAccount.");
		}

		long balance = ownerAccount.getBalance();

		if (amount <= 0) {
			throw new ContractValidateException("Amount must greater than 0.");
		}

		try {
			AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
			if (toAccount == null) {
				fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
			}

			if (balance < Math.addExact(amount, fee)) {
				throw new ContractValidateException(
					"Validate TransferContract error, balance is not sufficient.");
			}

			if (toAccount != null) {
				long toAddressBalance = Math.addExact(toAccount.getBalance(), amount);
			}
		} catch (ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(TransferContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return ChainConstant.TRANSFER_FEE;
	}

}