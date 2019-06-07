package io.midasprotocol.core.util;

import io.midasprotocol.core.config.NodeTier;

import static io.midasprotocol.core.config.Parameter.NodeConstant.NODE_TIERS;

public class StakeUtil {
	public static long getVotingPowerFromStakeAmount(long stakeAmount) {
		for (NodeTier tier : NODE_TIERS) {
			if (stakeAmount >= tier.getStakeAmount()) {
				return tier.getVotingPower();
			}
		}
		return 0;
	}

	public static long getStakeAmountWithBonusFromStakeAmount(long stakeAmount) {
		for (NodeTier tier : NODE_TIERS) {
			if (stakeAmount >= tier.getStakeAmount()) {
				return (long) (((double) tier.getBonus() / 100 + 1) * stakeAmount);
			}
		}
		return 0;
	}
}
