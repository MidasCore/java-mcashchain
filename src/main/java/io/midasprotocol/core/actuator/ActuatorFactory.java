package io.midasprotocol.core.actuator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction.Contract;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j(topic = "actuator")
public class ActuatorFactory {

	public static final ActuatorFactory INSTANCE = new ActuatorFactory();

	private ActuatorFactory() {
	}

	public static ActuatorFactory getInstance() {
		return INSTANCE;
	}

	/**
	 * create actuator.
	 */
	public static List<Actuator> createActuator(TransactionCapsule transactionCapsule,
												Manager manager) {
		List<Actuator> actuatorList = Lists.newArrayList();
		if (null == transactionCapsule || null == transactionCapsule.getInstance()) {
			logger.info("transactionCapsule or Transaction is null");
			return actuatorList;
		}

		Preconditions.checkNotNull(manager, "manager is null");
		Protocol.Transaction.Raw rawData = transactionCapsule.getInstance().getRawData();
		rawData.getContractList()
			.forEach(contract -> actuatorList.add(getActuatorByContract(contract, manager)));
		return actuatorList;
	}

	private static Actuator getActuatorByContract(Contract contract, Manager manager) {
		switch (contract.getType()) {
			case AccountUpdateContract:
				return new UpdateAccountActuator(contract.getParameter(), manager);
			case TransferContract:
				return new TransferActuator(contract.getParameter(), manager);
			case TransferAssetContract:
				return new TransferAssetActuator(contract.getParameter(), manager);
			case VoteAssetContract:
				break;
			case VoteWitnessContract:
				return new VoteWitnessActuator(contract.getParameter(), manager);
			case WitnessCreateContract:
				return new WitnessCreateActuator(contract.getParameter(), manager);
			case AccountCreateContract:
				return new CreateAccountActuator(contract.getParameter(), manager);
			case AssetIssueContract:
				return new AssetIssueActuator(contract.getParameter(), manager);
			case UnfreezeAssetContract:
				return new UnfreezeAssetActuator(contract.getParameter(), manager);
			case WitnessUpdateContract:
				return new WitnessUpdateActuator(contract.getParameter(), manager);
			case ParticipateAssetIssueContract:
				return new ParticipateAssetIssueActuator(contract.getParameter(), manager);
			case FreezeBalanceContract:
				return new FreezeBalanceActuator(contract.getParameter(), manager);
			case UnfreezeBalanceContract:
				return new UnfreezeBalanceActuator(contract.getParameter(), manager);
			case WithdrawBalanceContract:
				return new WithdrawBalanceActuator(contract.getParameter(), manager);
			case UpdateAssetContract:
				return new UpdateAssetActuator(contract.getParameter(), manager);
			case ProposalCreateContract:
				return new ProposalCreateActuator(contract.getParameter(), manager);
			case ProposalApproveContract:
				return new ProposalApproveActuator(contract.getParameter(), manager);
			case ProposalDeleteContract:
				return new ProposalDeleteActuator(contract.getParameter(), manager);
			case SetAccountIdContract:
				return new SetAccountIdActuator(contract.getParameter(), manager);
			case UpdateSettingContract:
				return new UpdateSettingContractActuator(contract.getParameter(), manager);
			case UpdateEnergyLimitContract:
				return new UpdateEnergyLimitContractActuator(contract.getParameter(), manager);
			case ExchangeCreateContract:
				return new ExchangeCreateActuator(contract.getParameter(), manager);
			case ExchangeInjectContract:
				return new ExchangeInjectActuator(contract.getParameter(), manager);
			case ExchangeWithdrawContract:
				return new ExchangeWithdrawActuator(contract.getParameter(), manager);
			case ExchangeTransactionContract:
				return new ExchangeTransactionActuator(contract.getParameter(), manager);
			case AccountPermissionUpdateContract:
				return new AccountPermissionUpdateActuator(contract.getParameter(), manager);
			case StakeContract:
				return new StakeActuator(contract.getParameter(), manager);
			case UnstakeContract:
				return new UnstakeActuator(contract.getParameter(), manager);
			case WitnessResignContract:
				return new WitnessResignActuator(contract.getParameter(), manager);
			case ClearAbiContract:
				return new ClearAbiContractActuator(contract.getParameter(), manager);
			// todo: add other contracts
			default:
				break;

		}
		return null;
	}

}
