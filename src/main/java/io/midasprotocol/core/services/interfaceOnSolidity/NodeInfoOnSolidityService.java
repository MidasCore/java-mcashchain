package io.midasprotocol.core.services.interfaceOnSolidity;

import io.midasprotocol.common.entity.NodeInfo;
import io.midasprotocol.core.services.NodeInfoService;
import org.springframework.stereotype.Component;

@Component
public class NodeInfoOnSolidityService extends NodeInfoService {

	@Override
	protected void setBlockInfo(NodeInfo nodeInfo) {
		super.setBlockInfo(nodeInfo);
		nodeInfo.setBlock(nodeInfo.getSolidityBlock());
		nodeInfo.setBeginSyncNum(-1);
	}

	@Override
	protected void setCheatWitnessInfo(NodeInfo nodeInfo) {
	}

}
