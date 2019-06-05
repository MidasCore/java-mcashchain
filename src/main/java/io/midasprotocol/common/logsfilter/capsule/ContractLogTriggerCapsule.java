package io.midasprotocol.common.logsfilter.capsule;

import io.midasprotocol.common.logsfilter.EventPluginLoader;
import io.midasprotocol.common.logsfilter.FilterQuery;
import io.midasprotocol.common.logsfilter.trigger.ContractLogTrigger;
import lombok.Getter;
import lombok.Setter;

public class ContractLogTriggerCapsule extends TriggerCapsule {

	@Getter
	@Setter
	ContractLogTrigger contractLogTrigger;

	public ContractLogTriggerCapsule(ContractLogTrigger contractLogTrigger) {
		this.contractLogTrigger = contractLogTrigger;
	}

	public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
		contractLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
	}

	@Override
	public void processTrigger() {
		if (FilterQuery.matchFilter(contractLogTrigger)) {
			EventPluginLoader.getInstance().postContractLogTrigger(contractLogTrigger);
		}
	}
}
