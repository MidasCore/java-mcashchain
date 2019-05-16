package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.ProposalCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.protos.Contract.ProposalDeleteContract;
import io.midasprotocol.protos.Protocol.Proposal.State;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static io.midasprotocol.core.actuator.ActuatorConstant.*;

@Slf4j(topic = "actuator")
public class ProposalDeleteActuator extends AbstractActuator {

	ProposalDeleteActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ProposalDeleteContract proposalDeleteContract = this.contract
					.unpack(ProposalDeleteContract.class);
			ProposalCapsule proposalCapsule = (Objects.isNull(deposit)) ? dbManager.getProposalStore().
					get(ByteArray.fromLong(proposalDeleteContract.getProposalId())) :
					deposit.getProposalCapsule(ByteArray.fromLong(proposalDeleteContract.getProposalId()));

			proposalCapsule.setState(State.CANCELED);
			if (Objects.isNull(deposit)) {
				dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
			} else {
				deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
			}

			ret.setStatus(fee, code.SUCCESS);
		} catch (InvalidProtocolBufferException | ItemNotFoundException e) {
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
		if (dbManager == null && (deposit == null || deposit.getDbManager() == null)) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(ProposalDeleteContract.class)) {
			throw new ContractValidateException(
					"Contract type error, expected ProposalDeleteContract, actual " + contract.getClass());
		}
		final ProposalDeleteContract contract;
		try {
			contract = this.contract.unpack(ProposalDeleteContract.class);
		} catch (InvalidProtocolBufferException e) {
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		if (!Objects.isNull(deposit)) {
			if (Objects.isNull(deposit.getAccount(ownerAddress))) {
				throw new ContractValidateException(
						ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
			}
		} else if (!dbManager.getAccountStore().has(ownerAddress)) {
			throw new ContractValidateException(ACCOUNT_EXCEPTION_STR + readableOwnerAddress
					+ NOT_EXIST_STR);
		}

		long latestProposalNum = Objects.isNull(deposit) ? dbManager.getDynamicPropertiesStore()
				.getLatestProposalNum() : deposit.getLatestProposalNum();
		if (contract.getProposalId() > latestProposalNum) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
					+ NOT_EXIST_STR);
		}

		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = Objects.isNull(getDeposit()) ? dbManager.getProposalStore().
					get(ByteArray.fromLong(contract.getProposalId())) :
					deposit.getProposalCapsule(ByteArray.fromLong(contract.getProposalId()));
		} catch (ItemNotFoundException ex) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
					+ NOT_EXIST_STR);
		}

		long now = dbManager.getHeadBlockTimeStamp();
		if (!proposalCapsule.getProposalAddress().equals(contract.getOwnerAddress())) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
					+ " is not proposed by " + readableOwnerAddress);
		}
		if (now >= proposalCapsule.getExpirationTime()) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
					+ " expired");
		}
		if (proposalCapsule.getState() == State.CANCELED) {
			throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + contract.getProposalId()
					+ " canceled");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ProposalDeleteContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
