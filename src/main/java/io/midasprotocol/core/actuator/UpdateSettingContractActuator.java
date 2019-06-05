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
import io.midasprotocol.protos.Contract.UpdateSettingContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class UpdateSettingContractActuator extends AbstractActuator {

	UpdateSettingContractActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			UpdateSettingContract usContract = contract
				.unpack(UpdateSettingContract.class);
			long newPercent = usContract.getConsumeUserResourcePercent();
			byte[] contractAddress = usContract.getContractAddress().toByteArray();
			ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);

			dbManager.getContractStore().put(contractAddress, new ContractCapsule(
				deployedContract.getInstance().toBuilder().setConsumeUserResourcePercent(newPercent)
					.build()));

			ret.setStatus(fee, code.SUCCESS);
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
		if (!this.contract.is(UpdateSettingContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected UpdateSettingContract, actual" + contract.getClass());
		}
		final UpdateSettingContract contract;
		try {
			contract = this.contract.unpack(UpdateSettingContract.class);
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

		long newPercent = contract.getConsumeUserResourcePercent();
		if (newPercent > 100 || newPercent < 0) {
			throw new ContractValidateException("Percent not in [0, 100]");
		}

		byte[] contractAddress = contract.getContractAddress().toByteArray();
		ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);

		if (deployedContract == null) {
			throw new ContractValidateException("Contract does not exist");
		}

		byte[] deployedContractOwnerAddress = deployedContract.getInstance().getOriginAddress()
			.toByteArray();

		if (!Arrays.equals(ownerAddress, deployedContractOwnerAddress)) {
			throw new ContractValidateException("Account " + readableOwnerAddress + " is not the owner of the contract");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(UpdateSettingContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
