package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.Parameter;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Contract.WitnessUpdateContract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class WitnessResignActuator extends AbstractActuator {

	WitnessResignActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	private void resignWitness(final Contract.WitnessResignContract contract) {
		WitnessCapsule witnessCapsule = this.dbManager.getWitnessStore()
				.get(contract.getSupernodeAddress().toByteArray());
		witnessCapsule.setStatus(Protocol.Witness.Status.RESIGNED);

		this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);
//		this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);

		ByteString ownerAddress = contract.getOwnerAddress();
		AccountCapsule owner = this.dbManager.getAccountStore().get(ownerAddress.toByteArray());

		// combine stake for supernode and normal stake with 30 days duration
		long now = dbManager.getHeadBlockTimeStamp();
		long duration = Parameter.ChainConstant.RESIGN_STAKE_TIME_IN_DAY * Parameter.TimeConstant.MS_PER_DAY;
		long newStake = owner.getTotalStakeAmount();

		owner.clearStakeSupernode();
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
			ret.setStatus(fee, code.SUCCESS);
		} catch (final InvalidProtocolBufferException e) {
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

		byte[] supernodeAddress = contract.getSupernodeAddress().toByteArray();
		if (!Wallet.addressValid(supernodeAddress)) {
			throw new ContractValidateException("Invalid supernodeAddress");
		}
		if (!this.dbManager.getAccountStore().has(supernodeAddress)) {
			throw new ContractValidateException("Supernode account does not exist");
		}

		if (!this.dbManager.getWitnessStore().has(supernodeAddress)) {
			throw new ContractValidateException("Witness does not exist");
		}

		WitnessCapsule witnessCapsule = this.dbManager.getWitnessStore().get(supernodeAddress);
		if (!witnessCapsule.getOwnerAddress().equals(ByteString.copyFrom(ownerAddress))) {
			throw new ContractValidateException("Address " + StringUtil.createReadableString(ownerAddress) +
					" does not own supernode " +
					StringUtil.createReadableString(supernodeAddress));
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
