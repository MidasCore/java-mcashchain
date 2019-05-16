package io.midasprotocol.core.services.interfaceOnSolidity;

import org.springframework.stereotype.Component;
import io.midasprotocol.common.entity.NodeInfo;
import io.midasprotocol.core.services.NodeInfoService;

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
