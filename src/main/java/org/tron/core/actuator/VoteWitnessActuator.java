package org.tron.core.actuator;

import static org.tron.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.tron.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.tron.core.actuator.ActuatorConstant.WITNESS_EXCEPTION_STR;

import com.google.common.math.LongMath;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.tron.common.storage.Deposit;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.VoteChangeCapsule;
import org.tron.core.db.AccountStore;
import org.tron.core.db.Manager;
import org.tron.core.db.VotesStore;
import org.tron.core.db.WitnessStore;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.VoteWitnessContract;
import org.tron.protos.Contract.VoteWitnessContract.Vote;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class VoteWitnessActuator extends AbstractActuator {


	VoteWitnessActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			VoteWitnessContract voteContract = contract.unpack(VoteWitnessContract.class);
			countVoteAccount(voteContract, getDeposit());
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
		if (dbManager == null && (getDeposit() == null || getDeposit().getDbManager() == null)) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(VoteWitnessContract.class)) {
			throw new ContractValidateException(
					"contract type error,expected type [VoteWitnessContract],real type[" + contract
							.getClass() + "]");
		}
		final VoteWitnessContract contract;
		try {
			contract = this.contract.unpack(VoteWitnessContract.class);
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
		WitnessStore witnessStore = dbManager.getWitnessStore();

		if (!contract.hasVote()) {
			throw new ContractValidateException("VoteNumber must more than 0");
		}
		try {
			long sum = 0L;
			Vote vote = contract.getVote();
			byte[] witnessCandidate = vote.getVoteAddress().toByteArray();
			if (!Wallet.addressValid(witnessCandidate)) {
				throw new ContractValidateException("Invalid vote address!");
			}
			long voteCount = vote.getVoteCount();
			if (voteCount <= 0) {
				throw new ContractValidateException("vote count must be greater than 0");
			}
			String readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());
			if (!Objects.isNull(getDeposit())) {
				if (Objects.isNull(getDeposit().getAccount(witnessCandidate))) {
					throw new ContractValidateException(
							ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
				}
			} else if (!accountStore.has(witnessCandidate)) {
				throw new ContractValidateException(
						ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
			}
			if (!Objects.isNull(getDeposit())) {
				if (Objects.isNull(getDeposit().getWitness(witnessCandidate))) {
					throw new ContractValidateException(
							WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
				}
			} else if (!witnessStore.has(witnessCandidate)) {
				throw new ContractValidateException(
						WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
			}
			sum = LongMath.checkedAdd(sum, vote.getVoteCount());

			AccountCapsule accountCapsule =
					(Objects.isNull(getDeposit())) ? accountStore.get(ownerAddress)
							: getDeposit().getAccount(ownerAddress);
			if (accountCapsule == null) {
				throw new ContractValidateException(
						ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
			}

			long votingPower = accountCapsule.getVotingPower();

			if (sum > votingPower) {
				throw new ContractValidateException(
						"The total number of votes[" + sum + "] is greater than the votingPower[" + votingPower + "]");
			}
		} catch (ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		return true;
	}

	private void countVoteAccount(VoteWitnessContract voteContract, Deposit deposit) {
		byte[] ownerAddress = voteContract.getOwnerAddress().toByteArray();

		VoteChangeCapsule voteChangeCapsule;
		VotesStore votesStore = dbManager.getVotesStore();
		AccountStore accountStore = dbManager.getAccountStore();

		AccountCapsule accountCapsule = (Objects.isNull(getDeposit())) ? accountStore.get(ownerAddress)
				: getDeposit().getAccount(ownerAddress);

		if (!Objects.isNull(getDeposit())) {
			VoteChangeCapsule vCapsule = getDeposit().getVotesCapsule(ownerAddress);
			if (Objects.isNull(vCapsule)) {
				voteChangeCapsule = new VoteChangeCapsule(voteContract.getOwnerAddress(),
						accountCapsule.getVote());
			} else {
				voteChangeCapsule = vCapsule;
			}
		} else if (!votesStore.has(ownerAddress)) {
			voteChangeCapsule = new VoteChangeCapsule(voteContract.getOwnerAddress(),
					accountCapsule.getVote());
		} else {
			voteChangeCapsule = votesStore.get(ownerAddress);
		}

		accountCapsule.clearVote();
		voteChangeCapsule.clearNewVote();

		if (voteContract.hasVote()) {
			logger.debug("countVoteAccount,address[{}]",
					ByteArray.toHexString(voteContract.getVote().getVoteAddress().toByteArray()));
			voteChangeCapsule.setNewVote(voteContract.getVote().getVoteAddress(), voteContract.getVote().getVoteCount());
			accountCapsule.setVote(voteContract.getVote().getVoteAddress(), voteContract.getVote().getVoteCount());
		}

		if (Objects.isNull(deposit)) {
			accountStore.put(accountCapsule.createDbKey(), accountCapsule);
			votesStore.put(ownerAddress, voteChangeCapsule);
		} else {
			// cache
			deposit.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
			deposit.putVoteValue(ownerAddress, voteChangeCapsule);
		}

	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(VoteWitnessContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
