package io.midasprotocol.core.witness;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.capsule.ProposalCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol.Proposal.State;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j(topic = "witness")
public class ProposalController {

	@Setter
	@Getter
	private Manager manager;

	public static ProposalController createInstance(Manager manager) {
		ProposalController instance = new ProposalController();
		instance.setManager(manager);
		return instance;
	}


	public void processProposals() {
		long latestProposalNum = manager.getDynamicPropertiesStore().getLatestProposalNum();
		if (latestProposalNum == 0) {
			logger.info("latestProposalNum is 0,return");
			return;
		}

		long proposalNum = latestProposalNum;

		ProposalCapsule proposalCapsule;

		while (proposalNum > 0) {
			try {
				proposalCapsule = manager.getProposalStore()
					.get(ProposalCapsule.calculateDbKey(proposalNum));
			} catch (Exception ex) {
				logger.error("", ex);
				continue;
			}

			if (proposalCapsule.hasProcessed()) {
				logger.info("Proposal has processed，id:[{}], skip it and before it", proposalCapsule.getId());
				//proposals with number less than this one, have been processed before
				break;
			}

			if (proposalCapsule.hasCanceled()) {
				logger.info("Proposal has canceled，id:[{}], skip it", proposalCapsule.getId());
				proposalNum--;
				continue;
			}

			long currentTime = manager.getDynamicPropertiesStore().getNextMaintenanceTime();
			if (proposalCapsule.hasExpired(currentTime)) {
				processProposal(proposalCapsule);
				proposalNum--;
				continue;
			}

			proposalNum--;
			logger.info("Proposal has not expired，id:[{}], skip it", proposalCapsule.getId());
		}
		logger.info("Processing proposals done, oldest proposal[{}]", proposalNum);
	}

	public boolean hasMostApprovals(ProposalCapsule proposalCapsule, List<ByteString> activeWitnesses) {
		long count = 0;
		List<ByteString> approvals = proposalCapsule.getApprovals();
		for (ByteString witness: activeWitnesses) {
			logger.info(StringUtil.createReadableString(witness));
			WitnessCapsule witnessCapsule = this.manager.getWitnessStore().get(witness.toByteArray());
			if (witnessCapsule != null && approvals.contains(witnessCapsule.getOwnerAddress())) {
				count++;
			}
		}
		return count > activeWitnesses.size() / 2;
	}

	public void processProposal(ProposalCapsule proposalCapsule) {
		List<ByteString> activeWitnesses = this.manager.getWitnessScheduleStore().getActiveWitnesses();
		if (this.hasMostApprovals(proposalCapsule, activeWitnesses)) {
			logger.info(
				"Processing proposal, id: {}, it has received most approvals, "
					+ "begin to set dynamic parameter: {}, "
					+ "and set proposal state as APPROVED",
				proposalCapsule.getId(), proposalCapsule.getParameters());
			setDynamicParameters(proposalCapsule);
			proposalCapsule.setState(State.APPROVED);
			manager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
		} else {
			logger.info(
				"Processing proposal, id: {}, "
					+ "it has not received enough approvals, set proposal state as DISAPPROVED",
				proposalCapsule.getId());
			proposalCapsule.setState(State.DISAPPROVED);
			manager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
		}

	}

	public void setDynamicParameters(ProposalCapsule proposalCapsule) {
		Map<Long, Long> map = proposalCapsule.getInstance().getParametersMap();
		for (Map.Entry<Long, Long> entry : map.entrySet()) {

			switch (entry.getKey().intValue()) {
				case (0): {
					manager.getDynamicPropertiesStore().saveMaintenanceTimeInterval(entry.getValue());
					break;
				}
				case (1): {
					manager.getDynamicPropertiesStore().saveAccountUpgradeCost(entry.getValue());
					break;
				}
				case (2): {
					manager.getDynamicPropertiesStore().saveCreateAccountFee(entry.getValue());
					break;
				}
				case (3): {
					manager.getDynamicPropertiesStore().saveTransactionFee(entry.getValue());
					break;
				}
				case (4): {
					manager.getDynamicPropertiesStore().saveAssetIssueFee(entry.getValue());
					break;
				}
				case (5): {
					manager.getDynamicPropertiesStore().saveWitnessPayPerBlock(entry.getValue());
					break;
				}
				case (6): {
					manager.getDynamicPropertiesStore()
						.saveCreateNewAccountFeeInSystemContract(entry.getValue());
					break;
				}
				case (7): {
					manager.getDynamicPropertiesStore().saveCreateNewAccountBandwidthRate(entry.getValue());
					break;
				}
				case (8): {
					manager.getDynamicPropertiesStore().saveAllowCreationOfContracts(entry.getValue());
					break;
				}
				case (9): {
					manager.getDynamicPropertiesStore().saveEnergyFee(entry.getValue());
					break;
				}
				case (10): {
					manager.getDynamicPropertiesStore().saveExchangeCreateFee(entry.getValue());
					break;
				}
				case (11): {
					manager.getDynamicPropertiesStore().saveMaxCpuTimeOfOneTx(entry.getValue());
					break;
				}
				case (12): {
					manager.getDynamicPropertiesStore().saveAllowUpdateAccountName(entry.getValue());
					break;
				}
				case (13): {
					manager.getDynamicPropertiesStore().saveAllowDelegateResource(entry.getValue());
					break;
				}
				case (14): {
					manager.getDynamicPropertiesStore().saveTotalEnergyLimit(entry.getValue());
					break;
				}
				case (15): {
					manager.getDynamicPropertiesStore().saveAllowTvmTransferM1(entry.getValue());
					break;
				}
				case (16): {
					manager.getDynamicPropertiesStore().saveTotalEnergyLimit2(entry.getValue());
					break;
				}
				case (17): {
					if (manager.getDynamicPropertiesStore().getAllowMultiSign() == 0) {
						manager.getDynamicPropertiesStore().saveAllowMultiSign(entry.getValue());
					}
					break;
				}
				case (18): {
					if (manager.getDynamicPropertiesStore().getAllowAdaptiveEnergy() == 0) {
						manager.getDynamicPropertiesStore().saveAllowAdaptiveEnergy(entry.getValue());
					}
					break;
				}
				case (19): {
					manager.getDynamicPropertiesStore().saveUpdateAccountPermissionFee(entry.getValue());
					break;
				}
				case (20): {
					manager.getDynamicPropertiesStore().saveMultiSignFee(entry.getValue());
					break;
				}
				case (21): {
					if (manager.getDynamicPropertiesStore().getAllowProtoFilter() == 0) {
						manager.getDynamicPropertiesStore().saveAllowProtoFilter(entry.getValue());
					}
					break;
				}
				default:
					break;
			}
		}
	}


}
