package org.tron.core.actuator;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.*;
import org.tron.core.db.Manager;
import org.tron.core.db.StakeAccountStore;
import org.tron.core.db.StakeChangeStore;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.util.Iterator;
import java.util.List;

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
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		byte[] ownerAddress = unstakeContract.getOwnerAddress().toByteArray();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		long oldBalance = accountCapsule.getBalance();

		long unstakeAmount = 0L;


		List<Protocol.Stake> stakesList = Lists.newArrayList();
		stakesList.addAll(accountCapsule.getStakeList());

		Iterator<Protocol.Stake> iterator = stakesList.iterator();
		long now = dbManager.getHeadBlockTimeStamp();
		while (iterator.hasNext()) {
			Protocol.Stake next = iterator.next();
			if (next.getExpireTime() <= now) {
				unstakeAmount += next.getStakeAmount();
				iterator.remove();
			}
		}

		accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
				.setBalance(oldBalance + unstakeAmount)
				.clearStakes().addAllStakes(stakesList).build());

//		dbManager.getDynamicPropertiesStore()
//				.addTotalNetWeight(-unstakeAmount / 1_000_000L);

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

		ret.setUnstakeAmount(unstakeAmount);
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
		if (!this.contract.is(Contract.UnstakeContract.class)) {
			throw new ContractValidateException(
					"contract type error, expected type [Contract.UnstakeContract], real type["
							+ contract.getClass() + "]");
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
					"Account [" + readableOwnerAddress + "] does not exist");
		}
		long now = dbManager.getHeadBlockTimeStamp();
		if (accountCapsule.getStakeAmount() <= 0) {
			throw new ContractValidateException("no stake amount");
		}

		long allowedUnstakeCount = accountCapsule.getStakeList().stream()
				.filter(stake -> stake.getExpireTime() <= now).count();
		if (allowedUnstakeCount <= 0) {
			throw new ContractValidateException("It's not time to unstake.");
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

		long stakeAmount = accountCapsule.getStakeAmount();

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
