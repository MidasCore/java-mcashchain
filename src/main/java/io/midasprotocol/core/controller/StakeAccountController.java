package io.midasprotocol.core.controller;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.capsule.BlockRewardCapsule;
import io.midasprotocol.core.capsule.StakeAccountCapsule;
import io.midasprotocol.core.capsule.StakeChangeCapsule;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.db.StakeAccountStore;
import io.midasprotocol.core.db.StakeChangeStore;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.util.RewardUtil;
import io.midasprotocol.core.util.StakeUtil;
import io.midasprotocol.protos.Protocol;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
		long totalPay = RewardUtil.rewardInflation(manager.getDynamicPropertiesStore().getStakingRewardPerEpoch(),
			manager.getHeadBlockNum(), Parameter.ChainConstant.BLOCKS_PER_YEAR);

		long blockNumber = manager.getHeadBlockNum();
		BlockRewardCapsule blockRewardCapsule = manager.getBlockRewardStore().get(ByteArray.fromLong(blockNumber));
		if (blockRewardCapsule == null) {
			blockRewardCapsule = new BlockRewardCapsule(blockNumber);
		}

		for (Map.Entry<ByteString, Long> entry : stakes.entrySet()) {
			ByteString address = entry.getKey();
			long reward = (long) (entry.getValue() * ((double) totalPay / stakeSum));
			String readableAddress = StringUtil.createReadableString(address);
			logger.info("Paying {} MCASH to {}", reward, readableAddress);
			try {
				manager.adjustAllowance(address.toByteArray(), reward);
				blockRewardCapsule.addReward(address, reward, Protocol.BlockReward.RewardType.STAKE);
			} catch (BalanceInsufficientException e) {
				logger.error("{}", e.toString());
			}
		}
		manager.getBlockRewardStore().put(blockRewardCapsule.createDbKey(), blockRewardCapsule);
	}


}
