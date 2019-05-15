package org.tron.core.controller;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.StakeAccountCapsule;
import org.tron.core.capsule.StakeChangeCapsule;
import org.tron.core.capsule.utils.StakeUtil;
import org.tron.core.db.Manager;
import org.tron.core.db.StakeAccountStore;
import org.tron.core.db.StakeChangeStore;
import org.tron.core.exception.BalanceInsufficientException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "staking-node")
public class StakeAccountController {
	@Setter
	@Getter
	private Manager manager;

	public static StakeAccountController createInstance(Manager manager) {
		StakeAccountController instance = new StakeAccountController();
		instance.setManager(manager);
		return instance;
	}

	public void updateStakeAccount() {
		StakeAccountStore stakeAccountStore = manager.getStakeAccountStore();
		StakeChangeStore stakeChangeStore = manager.getStakeChangeStore();

		Map<ByteString, Long> stakes = Maps.newHashMap();
		long stakeSum = 0L;

		List<StakeAccountCapsule> nodes = stakeAccountStore.getAllStakeAccounts();
		for (StakeAccountCapsule stakeAccountCapsule : nodes) {
			stakes.put(stakeAccountCapsule.getAddress(), stakeAccountCapsule.getStakeAmountWithBonus());
			stakeSum += stakeAccountCapsule.getStakeAmountWithBonus();
		}

		payStakeAccount(stakes, stakeSum);

		Iterator<Map.Entry<byte[], StakeChangeCapsule>> dbIterator = stakeChangeStore.iterator();

		while (dbIterator.hasNext()) {
			Map.Entry<byte[], StakeChangeCapsule> next = dbIterator.next();
			StakeChangeCapsule stakeChange = next.getValue();

			if (stakeChange.hasNewStakeAmount()) {
				if (stakeChange.getNewStakeAmount() > 0 && StakeUtil.getVotingPowerFromStakeAmount(stakeChange.getNewStakeAmount()) > 0) {
					StakeAccountCapsule stakeAccountCapsule = stakeAccountStore.get(next.getKey());
					if (stakeAccountCapsule == null) {
						stakeAccountCapsule = new StakeAccountCapsule(stakeChange.getAddress());
					}
					stakeAccountCapsule.setStake(stakeChange.getNewStakeAmount());
					stakeAccountStore.put(next.getKey(), stakeAccountCapsule);
				} else {
					stakeAccountStore.delete(next.getKey());
				}
			}
			stakeChangeStore.delete(next.getKey());
		}
	}

	private void payStakeAccount(Map<ByteString, Long> stakes, long stakeSum) {
		long totalPay = manager.getDynamicPropertiesStore().getStakingRewardPerEpoch();
		for (Map.Entry<ByteString, Long> entry : stakes.entrySet()) {
			long pay = (long) (entry.getValue() * ((double) totalPay / stakeSum));
			String readableAddress = StringUtil.createReadableString(entry.getKey());
			logger.info("Paying {} MCASH to {}", pay, readableAddress);
			try {
				manager.adjustAllowance(entry.getKey().toByteArray(), pay);
			} catch (BalanceInsufficientException e) {
				logger.error("{}", e.toString());
			}
		}
	}


}
