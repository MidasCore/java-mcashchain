package io.midasprotocol.common.logsfilter.capsule;

import io.midasprotocol.common.logsfilter.ContractEventParser;
import io.midasprotocol.common.logsfilter.EventPluginLoader;
import io.midasprotocol.common.logsfilter.FilterQuery;
import io.midasprotocol.common.logsfilter.trigger.ContractEventTrigger;
import io.midasprotocol.common.runtime.vm.LogEventWrapper;
import io.midasprotocol.protos.Protocol.SmartContract.ABI.Entry;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ContractEventTriggerCapsule extends TriggerCapsule {

	@Getter
	@Setter
	ContractEventTrigger contractEventTrigger;
	@Getter
	@Setter
	private List<byte[]> topicList;
	@Getter
	@Setter
	private byte[] data;
	@Getter
	@Setter
	private Entry abiEntry;

	public ContractEventTriggerCapsule(LogEventWrapper log) {
		this.contractEventTrigger = new ContractEventTrigger();

		this.contractEventTrigger.setUniqueId(log.getUniqueId());
		this.contractEventTrigger.setTransactionId(log.getTransactionId());
		this.contractEventTrigger.setContractAddress(log.getContractAddress());
		this.contractEventTrigger.setCallerAddress(log.getCallerAddress());
		this.contractEventTrigger.setOriginAddress(log.getOriginAddress());
		this.contractEventTrigger.setCreatorAddress(log.getCreatorAddress());
		this.contractEventTrigger.setBlockNumber(log.getBlockNumber());
		this.contractEventTrigger.setTimeStamp(log.getTimeStamp());

		this.topicList = log.getTopicList();
		this.data = log.getData();
		this.contractEventTrigger.setEventSignature(log.getEventSignature());
		this.contractEventTrigger.setEventSignatureFull(log.getEventSignatureFull());
		this.contractEventTrigger.setEventName(log.getAbiEntry().getName());
		this.abiEntry = log.getAbiEntry();
	}

	public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
		contractEventTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
	}

	@Override
	public void processTrigger() {
		contractEventTrigger.setTopicMap(ContractEventParser.parseTopics(topicList, abiEntry));
		contractEventTrigger.setDataMap(ContractEventParser.parseEventData(data, topicList, abiEntry));

		if (FilterQuery.matchFilter(contractEventTrigger)) {
			EventPluginLoader.getInstance().postContractEventTrigger(contractEventTrigger);
		}
	}
}
