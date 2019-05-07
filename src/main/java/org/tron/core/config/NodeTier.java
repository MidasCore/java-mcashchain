package org.tron.core.config;

import lombok.Getter;

public class NodeTier {
	@Getter
	private String name;
	@Getter
	private long stakeAmount;
	@Getter
	private int votingPower;
	@Getter
	private int bonus;

	NodeTier(String name, long stakeAmount, int votingPower, int bonus) {
		this.name = name;
		this.stakeAmount = stakeAmount;
		this.votingPower = votingPower;
		this.bonus = bonus;
	}
}
