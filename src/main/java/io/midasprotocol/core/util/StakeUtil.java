package io.midasprotocol.core.util;

import io.midasprotocol.core.config.NodeTier;

import static io.midasprotocol.core.config.Parameter.NodeConstant.NODE_TIERS;

public class StakeUtil {
	public static long getVotingPowerFromStakeAmount(long stakeAmount) {
		long votingPower = 0;

		for (NodeTier tier : NODE_TIERS) {
			long k = stakeAmount / tier.getStakeAmount();
			votingPower += k * tier.getVotingPower();
			stakeAmount -= k * tier.getStakeAmount();
		}
		return votingPower;
	}

	public static long getStakeAmountWithBonusFromStakeAmount(long stakeAmount) {
		long ret = 0;
		for (NodeTier tier : NODE_TIERS) {
			long k = stakeAmount / tier.getStakeAmount();
			ret += (long) (((double) tier.getBonus() / 100 + 1) * k * tier.getStakeAmount());
			stakeAmount -= k * tier.getStakeAmount();
		}
		return ret;
	}
}
