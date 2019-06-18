package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ProposalCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.Parameter.ChainParameters;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.ProposalCreateContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

import static io.midasprotocol.core.actuator.ActuatorConstant.*;

//import io.midasprotocol.core.config.Parameter.ForkBlockVersionConsts;
//import io.midasprotocol.core.config.Parameter.ForkBlockVersionEnum;

@Slf4j(topic = "actuator")
public class ProposalCreateActuator extends AbstractActuator {

	ProposalCreateActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ProposalCreateContract proposalCreateContract = this.contract
				.unpack(ProposalCreateContract.class);
			long id = (Objects.isNull(getDeposit())) ?
				dbManager.getDynamicPropertiesStore().getLatestProposalNum() + 1 :
				getDeposit().getLatestProposalNum() + 1;
			ProposalCapsule proposalCapsule =
				new ProposalCapsule(proposalCreateContract.getOwnerAddress(), id);

			proposalCapsule.setParameters(proposalCreateContract.getParametersMap());

			long now = dbManager.getHeadBlockTimeStamp();
			long maintenanceTimeInterval = (Objects.isNull(getDeposit())) ?
				dbManager.getDynamicPropertiesStore().getMaintenanceTimeInterval() :
				getDeposit().getMaintenanceTimeInterval();
			proposalCapsule.setCreateTime(now);

			long currentMaintenanceTime =
				(Objects.isNull(getDeposit())) ? dbManager.getDynamicPropertiesStore()
					.getNextMaintenanceTime() :
					getDeposit().getNextMaintenanceTime();
			long now3 = now + Args.getInstance().getProposalExpireTime();
			long round = (now3 - currentMaintenanceTime) / maintenanceTimeInterval;
			long expirationTime =
				currentMaintenanceTime + (round + 1) * maintenanceTimeInterval;
			proposalCapsule.setExpirationTime(expirationTime);

			if (Objects.isNull(deposit)) {
				dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
				dbManager.getDynamicPropertiesStore().saveLatestProposalNum(id);
			} else {
				deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
				deposit.putDynamicPropertiesWithLatestProposalNum(id);
			}

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
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (dbManager == null && (deposit == null || deposit.getDbManager() == null)) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(ProposalCreateContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected ProposalCreateContract, actual " + contract.getClass());
		}
		final ProposalCreateContract contract;
		try {
			contract = this.contract.unpack(ProposalCreateContract.class);
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

		if (contract.getParametersMap().size() == 0) {
			throw new ContractValidateException("This proposal has no parameter.");
		}

		for (Map.Entry<Long, Long> entry : contract.getParametersMap().entrySet()) {
			if (!validKey(entry.getKey())) {
				throw new ContractValidateException("Bad chain parameter id");
			}
			validateValue(entry);
		}

		return true;
	}

	private void validateValue(Map.Entry<Long, Long> entry) throws ContractValidateException {

		switch (entry.getKey().intValue()) {
			// maintenance interval of SR
			case (0): {
				if (entry.getValue() < 3 * 27 * 1000 || entry.getValue() > 24 * 3600 * 1000) {
					throw new ContractValidateException(
						"Bad chain parameter value, valid range is [3 * 27 * 1000, 24 * 3600 * 1000]");
				}
				return;
			}
			// cost of applying for SR account
			case (1):
				// account creation fee
			case (2):
				// amount of TRX used to gain extra bandwidth
			case (3):
				// asset issuance fee
			case (4):
				// SR block generation reward
			case (5):
				// cost of account creation
			case (6):
				// consumption of bandwidth
			case (7): {
				if (entry.getValue() < 0 || entry.getValue() > 100_000_000_000_000_000L) {
					throw new ContractValidateException(
						"Bad chain parameter value, valid range is [0, 100_000_000_000_000_000L]");
				}
				break;
			}
			// Virtual Machine (VM)
			case (8): {
				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value ALLOW_CREATION_OF_CONTRACTS is only allowed to be 1");
				}
				break;
			}
			// GR Genesis votes
			case (9): {
				if (dbManager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr() == -1) {
					throw new ContractValidateException(
						"This proposal has been executed before and is only allowed to be executed once");
				}

				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value REMOVE_THE_POWER_OF_THE_GR is only allowed to be 1");
				}
				break;
			}
			// fee of 1 energy
			case (10):
				break;
			// cost of trading pair creation
			case (11):
				break;
			// maximum execution time of one transaction
			case (12):
				if (entry.getValue() < 10 || entry.getValue() > 100) {
					throw new ContractValidateException(
						"Bad chain parameter value,valid range is [10,100]");
				}
				break;
			// change the account name
			case (13): {
				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value ALLOW_UPDATE_ACCOUNT_NAME is only allowed to be 1");
				}
				break;
			}
			// resource delegation
			case (14): {
				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value ALLOW_DELEGATE_RESOURCE is only allowed to be 1");
				}
				break;
			}
			// total energy limit
			case (15): { // deprecated
//				if (!dbManager.getForkController().pass(ForkBlockVersionConsts.ENERGY_LIMIT)) {
//					throw new ContractValidateException("Bad chain parameter id");
//				}
//				if (dbManager.getForkController().pass(ForkBlockVersionEnum.VERSION_3_2_2)) {
//					throw new ContractValidateException("Bad chain parameter id");
//				}
				if (entry.getValue() < 0 || entry.getValue() > 100_000_000_000_000_000L) {
					throw new ContractValidateException(
						"Bad chain parameter value,valid range is [0, 100_000_000_000_000_000L]");
				}
				break;
			}
			// TRC-10 token transfer in smart contracts
			case (16): {
				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value ALLOW_TVM_TRANSFER_TRC10 is only allowed to be 1");
				}
				break;
			}
			// total energy limit
			case (17): {
//				if (!dbManager.getForkController().pass(ForkBlockVersionEnum.VERSION_3_2_2)) {
//					throw new ContractValidateException("Bad chain parameter id");
//				}
				if (entry.getValue() < 0 || entry.getValue() > 100_000_000_000_000_000L) {
					throw new ContractValidateException(
						"Bad chain parameter value,valid range is [0, 100_000_000_000_000_000L]");
				}
				break;
			}
			// initiation of multi-signature
			case (18): {
//				if (!dbManager.getForkController().pass(ForkBlockVersionEnum.VERSION_3_5)) {
//					throw new ContractValidateException("Bad chain parameter id: ALLOW_MULTI_SIGN");
//				}
				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value ALLOW_MULTI_SIGN is only allowed to be 1");
				}
				break;
			}
			// adaptive adjustment for total Energy
			case (19): {
//				if (!dbManager.getForkController().pass(ForkBlockVersionEnum.VERSION_3_5)) {
//					throw new ContractValidateException("Bad chain parameter id: ALLOW_ADAPTIVE_ENERGY");
//				}
				if (entry.getValue() != 1) {
					throw new ContractValidateException(
						"This value ALLOW_ADAPTIVE_ENERGY is only allowed to be 1");
				}
				break;
			}
			// fee for updating account permission
			case (20): {
//				if (!dbManager.getForkController().pass(ForkBlockVersionEnum.VERSION_3_5)) {
//					throw new ContractValidateException(
//							"Bad chain parameter id: UPDATE_ACCOUNT_PERMISSION_FEE");
//				}
				if (entry.getValue() < 0 || entry.getValue() > 100_000_000_000L) {
					throw new ContractValidateException(
						"Bad chain parameter value, valid range is [0, 100_000_000_000L]");
				}
				break;
			}
			// fee for multi-signature
			case (21): {
//				if (!dbManager.getForkController().pass(ForkBlockVersionEnum.VERSION_3_5)) {
//					throw new ContractValidateException("Bad chain parameter id: MULTI_SIGN_FEE");
//				}
				if (entry.getValue() < 0 || entry.getValue() > 100_000_000_000L) {
					throw new ContractValidateException(
						"Bad chain parameter value, valid range is [0, 100_000_000_000L]");
				}
				break;
			}
			default:
				break;
		}
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(ProposalCreateContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

	private boolean validKey(long idx) {
		return idx >= 0 && idx < ChainParameters.values().length;
	}

}
