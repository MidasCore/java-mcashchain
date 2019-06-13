package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ContractCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.AccountStore;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.UpdateEnergyLimitContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class UpdateEnergyLimitContractActuator extends AbstractActuator {

	UpdateEnergyLimitContractActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			UpdateEnergyLimitContract usContract = contract
				.unpack(UpdateEnergyLimitContract.class);
			long newOriginEnergyLimit = usContract.getOriginEnergyLimit();
			byte[] contractAddress = usContract.getContractAddress().toByteArray();
			ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);

			dbManager.getContractStore().put(contractAddress, new ContractCapsule(
				deployedContract.getInstance().toBuilder().setOriginEnergyLimit(newOriginEnergyLimit)
					.build()));

			ret.setStatus(fee, Code.SUCCESS);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		return true;
	}

	@Override
	public boolean validate() throws ContractValidateException {
//		if (!VMConfig.getEnergyLimitHardFork()) {
//			throw new ContractValidateException(
//					"Contract type error, unexpected type UpdateEnergyLimitContract");
//		}
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (this.dbManager == null) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(UpdateEnergyLimitContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected UpdateEnergyLimitContract, actual " + contract.getClass());
		}
		final UpdateEnergyLimitContract contract;
		try {
			contract = this.contract.unpack(UpdateEnergyLimitContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		if (!Wallet.addressValid(contract.getOwnerAddress().toByteArray())) {
			throw new ContractValidateException("Invalid address");
		}
		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

		AccountStore accountStore = dbManager.getAccountStore();

		AccountCapsule accountCapsule = accountStore.get(ownerAddress);
		if (accountCapsule == null) {
			throw new ContractValidateException("Account " + readableOwnerAddress + " does not exist");
		}

		long newOriginEnergyLimit = contract.getOriginEnergyLimit();
		if (newOriginEnergyLimit <= 0) {
			throw new ContractValidateException("Origin energy limit must > 0");
		}

		byte[] contractAddress = contract.getContractAddress().toByteArray();
		ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);

		if (deployedContract == null) {
			throw new ContractValidateException("Contract does not exist");
		}

		byte[] deployedContractOwnerAddress = deployedContract.getInstance().getOriginAddress()
			.toByteArray();

		if (!Arrays.equals(ownerAddress, deployedContractOwnerAddress)) {
			throw new ContractValidateException(
				"Account " + readableOwnerAddress + " is not the owner of the contract");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(UpdateEnergyLimitContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
