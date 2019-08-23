package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.runtime.config.VMConfig;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.*;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.db.StakeAccountStore;
import io.midasprotocol.core.db.StakeChangeStore;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "actuator")
public class UnstakeActuator extends AbstractActuator {

	UnstakeActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		final Contract.UnstakeContract unstakeContract;
		try {
			unstakeContract = contract.unpack(Contract.UnstakeContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		byte[] ownerAddress = unstakeContract.getOwnerAddress().toByteArray();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		long oldBalance = accountCapsule.getBalance();

		long unstakeAmount = 0L;

		long now = dbManager.getHeadBlockTimeStamp();
		Protocol.Stake stake = accountCapsule.getStake();
		if (stake.getExpirationTime() <= now) {
			unstakeAmount += stake.getStakeAmount();
		}

		if (unstakeAmount > 0) {
			accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
				.setBalance(oldBalance + unstakeAmount)
				.clearStake().build());

			VoteChangeCapsule voteChangeCapsule;
			if (!dbManager.getVoteChangeStore().has(ownerAddress)) {
				voteChangeCapsule = new VoteChangeCapsule(unstakeContract.getOwnerAddress(),
					accountCapsule.getVote());
			} else {
				voteChangeCapsule = dbManager.getVoteChangeStore().get(ownerAddress);
			}
			accountCapsule.clearVote();
			voteChangeCapsule.clearNewVote();

			dbManager.getAccountStore().put(ownerAddress, accountCapsule);

			dbManager.getVoteChangeStore().put(ownerAddress, voteChangeCapsule);

			this.recalculateStake(ownerAddress);

			dbManager.getDynamicPropertiesStore().addTotalStake(-unstakeAmount);
		}

		ret.setUnstakeAmount(unstakeAmount);
		ret.setStatus(fee, Code.SUCCESS);

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
		if (!this.contract.is(Contract.UnstakeContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected UnstakeContract, actual " + contract.getClass());
		}
		final Contract.UnstakeContract unstakeContract;
		try {
			unstakeContract = this.contract.unpack(Contract.UnstakeContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = unstakeContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
				"Account " + readableOwnerAddress + " does not exist");
		}
		long now = dbManager.getHeadBlockTimeStamp();
		if (accountCapsule.getNormalStakeAmount() <= 0) {
			throw new ContractValidateException("No stake amount");
		}

		if (accountCapsule.getStake().getExpirationTime() > now) {
			throw new ContractValidateException("It's not time to unstake");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(Contract.UnstakeContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

	private void recalculateStake(byte[] address) {
		StakeChangeStore stakeChangeStore = dbManager.getStakeChangeStore();
		StakeAccountStore stakeAccountStore = dbManager.getStakeAccountStore();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		if (accountCapsule == null) {
			return;
		}

		long stakeAmount = accountCapsule.getNormalStakeAmount();
		if (dbManager.getDynamicPropertiesStore().getAllowVmConstantinople() == 1) {
			stakeAmount = accountCapsule.getTotalStakeAmount();
		}

		StakeAccountCapsule stakeAccountCapsule = stakeAccountStore.get(address);
		if (stakeAccountCapsule == null) {
			return;
		}

		StakeChangeCapsule stakeChangeCapsule;

		if (!stakeChangeStore.has(address)) {
			stakeChangeCapsule = new StakeChangeCapsule(ByteString.copyFrom(address),
				stakeAccountCapsule.getStakeAmount());
		} else {
			stakeChangeCapsule = stakeChangeStore.get(address);
		}

		stakeChangeCapsule.setNewStakeAmount(stakeAmount);
		stakeChangeStore.put(address, stakeChangeCapsule);

		if (stakeAmount <= 0) {
			stakeAccountStore.delete(address);
		}
	}
}
