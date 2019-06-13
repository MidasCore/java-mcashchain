package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.WitnessUpdateContract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j(topic = "actuator")
public class WitnessResignActuator extends AbstractActuator {

	WitnessResignActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	private void resignWitness(final Contract.WitnessResignContract contract) {
		WitnessCapsule witnessCapsule = this.dbManager.getWitnessStore()
			.get(contract.getWitnessAddress().toByteArray());
		witnessCapsule.setStatus(Protocol.Witness.Status.RESIGNED);
		witnessCapsule.setIsJobs(false);

		this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);

		// todo: find better way to remove active witness
		List<ByteString> activeWitness = this.dbManager.getWitnessScheduleStore().getActiveWitnesses();
		if (activeWitness.remove(witnessCapsule.getAddress())) {
			this.dbManager.getWitnessScheduleStore().saveActiveWitnesses(activeWitness);
		}

		ByteString ownerAddress = contract.getOwnerAddress();
		AccountCapsule owner = this.dbManager.getAccountStore().get(ownerAddress.toByteArray());

		// combine stake for witness and normal stake with 30 days duration
		long now = dbManager.getHeadBlockTimeStamp();
		long duration = dbManager.getDynamicPropertiesStore().getResignStakeTimeInDay()
			* Parameter.TimeConstant.MS_PER_DAY;
		long newStake = owner.getTotalStakeAmount();

		owner.clearWitnessStake();
		owner.setStake(newStake, now + duration);

		this.dbManager.getAccountStore().put(ownerAddress.toByteArray(), owner);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final Contract.WitnessResignContract witnessResignContract = this.contract
				.unpack(Contract.WitnessResignContract.class);
			this.resignWitness(witnessResignContract);
			ret.setStatus(fee, Code.SUCCESS);
		} catch (final InvalidProtocolBufferException e) {
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
		if (!this.contract.is(Contract.WitnessResignContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected WitnessResignContract, actual " + contract.getClass());
		}
		final Contract.WitnessResignContract contract;
		try {
			contract = this.contract.unpack(Contract.WitnessResignContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}
		if (!this.dbManager.getAccountStore().has(ownerAddress)) {
			throw new ContractValidateException("Account does not exist");
		}

		byte[] witnessAddress = contract.getWitnessAddress().toByteArray();
		if (!Wallet.addressValid(witnessAddress)) {
			throw new ContractValidateException("Invalid witnessAddress");
		}
		if (!this.dbManager.getAccountStore().has(witnessAddress)) {
			throw new ContractValidateException("Witness account does not exist");
		}

		if (!this.dbManager.getWitnessStore().has(witnessAddress)) {
			throw new ContractValidateException("Witness does not exist");
		}

		WitnessCapsule witnessCapsule = this.dbManager.getWitnessStore().get(witnessAddress);
		if (!witnessCapsule.getOwnerAddress().equals(ByteString.copyFrom(ownerAddress))) {
			throw new ContractValidateException("Address " + StringUtil.createReadableString(ownerAddress) +
				" does not own witness " + StringUtil.createReadableString(witnessAddress));
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(WitnessUpdateContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
