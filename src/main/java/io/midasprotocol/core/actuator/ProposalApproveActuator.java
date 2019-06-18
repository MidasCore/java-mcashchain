package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ProposalCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.protos.Contract.ProposalApproveContract;
import io.midasprotocol.protos.Protocol.Proposal.State;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static io.midasprotocol.core.actuator.ActuatorConstant.*;

@Slf4j(topic = "actuator")
public class ProposalApproveActuator extends AbstractActuator {

	ProposalApproveActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ProposalApproveContract proposalApproveContract =
				this.contract.unpack(ProposalApproveContract.class);
			ProposalCapsule proposalCapsule =
				(Objects.isNull(getDeposit())) ? dbManager.getProposalStore()
					.get(ByteArray.fromLong(proposalApproveContract.getProposalId())) :
					getDeposit().getProposalCapsule(ByteArray.fromLong(proposalApproveContract
						.getProposalId()));

			ByteString committeeAddress = proposalApproveContract.getOwnerAddress();
			if (proposalApproveContract.getIsAddApproval()) {
				proposalCapsule.addApproval(committeeAddress);
			} else {
				proposalCapsule.removeApproval(committeeAddress);
			}
			if (Objects.isNull(deposit)) {
				dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
			} else {
				deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
			}
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
		if (dbManager == null && (getDeposit() == null || getDeposit().getDbManager() == null)) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(ProposalApproveContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected ProposalApproveContract, actual " + contract
					.getClass());
		}
		final ProposalApproveContract contract;
		try {
			contract = this.contract.unpack(ProposalApproveContract.class);
		} catch (InvalidProtocolBufferException e) {
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule ownerAccountCapsule;

		if (!Objects.isNull(deposit)) {
			ownerAccountCapsule = deposit.getAccount(ownerAddress);
			if (Objects.isNull(ownerAccountCapsule)) {
				throw new ContractValidateException(
					ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
			}
		} else {
			ownerAccountCapsule = dbManager.getAccountStore().get(ownerAddress);
			if (Objects.isNull(ownerAccountCapsule)) {
				throw new ContractValidateException(
					ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
			}
		}

		if (!ownerAccountCapsule.getIsCommittee()) {
			throw new ContractValidateException(
				ACCOUNT_EXCEPTION_STR + readableOwnerAddress + " does not have right");
		}

		long latestProposalNum = Objects.isNull(getDeposit()) ? dbManager.getDynamicPropertiesStore()
			.getLatestProposalNum() :
			getDeposit().getLatestProposalNum();
		if (contract.getProposalId() > latestProposalNum) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
				+ NOT_EXIST_STR);
		}

		long now = dbManager.getHeadBlockTimeStamp();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = Objects.isNull(getDeposit()) ? dbManager.getProposalStore().
				get(ByteArray.fromLong(contract.getProposalId())) :
				getDeposit().getProposalCapsule(ByteArray.fromLong(contract.getProposalId()));
		} catch (ItemNotFoundException ex) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
				+ NOT_EXIST_STR);
		}

		if (now >= proposalCapsule.getExpirationTime()) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
				+ " expired");
		}
		if (proposalCapsule.getState() == State.CANCELED) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
				+ " canceled");
		}
		if (!contract.getIsAddApproval()) {
			if (!proposalCapsule.getApprovals().contains(contract.getOwnerAddress())) {
				throw new ContractValidateException(
					WITNESS_EXCEPTION_STR + readableOwnerAddress + " has not approved proposal " + contract
						.getProposalId() + " before");
			}
		} else {
			if (proposalCapsule.getApprovals().contains(contract.getOwnerAddress())) {
				throw new ContractValidateException(
					WITNESS_EXCEPTION_STR + readableOwnerAddress + " has approved proposal " + contract
						.getProposalId() + " before");
			}
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ProposalApproveContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
