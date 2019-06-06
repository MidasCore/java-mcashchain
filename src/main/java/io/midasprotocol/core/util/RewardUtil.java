package io.midasprotocol.core.util;

public class RewardUtil {
	public static long rewardInflation(long reward, long blockNumber, long blockPerYear) {
		if (blockNumber < blockPerYear * 2)
			return reward;
		if (blockNumber < blockPerYear * 4)
			return reward / 2;
		if (blockNumber < blockPerYear * 6)
			return reward / 4;
		return reward / 8;
	}
}
