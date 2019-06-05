package io.midasprotocol.common.logsfilter.capsule;

import io.midasprotocol.common.logsfilter.EventPluginLoader;
import io.midasprotocol.common.logsfilter.trigger.BlockLogTrigger;
import io.midasprotocol.core.capsule.BlockCapsule;
import lombok.Getter;
import lombok.Setter;

public class BlockLogTriggerCapsule extends TriggerCapsule {

	@Getter
	@Setter
	BlockLogTrigger blockLogTrigger;

	public BlockLogTriggerCapsule(BlockCapsule block) {
		blockLogTrigger = new BlockLogTrigger();
		blockLogTrigger.setBlockHash(block.getBlockId().toString());
		blockLogTrigger.setTimeStamp(block.getTimeStamp());
		blockLogTrigger.setBlockNumber(block.getNum());
		blockLogTrigger.setTransactionSize(block.getTransactions().size());
		block.getTransactions().forEach(trx ->
			blockLogTrigger.getTransactionList().add(trx.getTransactionId().toString())
		);
	}

	public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
		blockLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
	}

	@Override
	public void processTrigger() {
		EventPluginLoader.getInstance().postBlockTrigger(blockLogTrigger);
	}
}
