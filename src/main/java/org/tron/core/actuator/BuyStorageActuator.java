package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.Manager;
import org.tron.core.db.StorageMarket;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.util.ConversionUtil;
import org.tron.protos.Contract.BuyStorageContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class BuyStorageActuator extends AbstractActuator {

	private StorageMarket storageMarket;

	BuyStorageActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
		storageMarket = new StorageMarket(dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		final BuyStorageContract buyStorageContract;
		try {
			buyStorageContract = contract.unpack(BuyStorageContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(buyStorageContract.getOwnerAddress().toByteArray());
		long quant = buyStorageContract.getQuant();

		storageMarket.buyStorage(accountCapsule, quant);

		ret.setStatus(fee, code.SUCCESS);

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
		if (!contract.is(BuyStorageContract.class)) {
			throw new ContractValidateException(
					"Contract type error, expected BuyStorageContract, actual " + contract.getClass());
		}

		final BuyStorageContract buyStorageContract;
		try {
			buyStorageContract = this.contract.unpack(BuyStorageContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = buyStorageContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
					"Account " + readableOwnerAddress + " does not exist");
		}

		long quant = buyStorageContract.getQuant();
		if (quant <= 0) {
			throw new ContractValidateException("Quantity must be positive");
		}

		if (quant < ConversionUtil.McashToMatoshi(1)) {
			throw new ContractValidateException("Quantity must be larger than 1 MCASH");
		}

		if (quant > accountCapsule.getBalance()) {
			throw new ContractValidateException("Quantity must be less than accountBalance");
		}
		long storageBytes = storageMarket.tryBuyStorage(quant);
		if (storageBytes < 1L) {
			throw new ContractValidateException(
					"storageBytes must be larger than 1, current storageBytes " + storageBytes);
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(BuyStorageContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
