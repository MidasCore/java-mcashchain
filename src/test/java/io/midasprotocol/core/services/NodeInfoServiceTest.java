package io.midasprotocol.core.services;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import io.midasprotocol.api.GrpcAPI.EmptyMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.api.WalletGrpc.WalletBlockingStub;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.entity.NodeInfo;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.program.Version;
import stest.tron.wallet.common.client.Configuration;

@Slf4j
public class NodeInfoServiceTest {

	private NodeInfoService nodeInfoService;
	private WitnessProductBlockService witnessProductBlockService;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);

	public NodeInfoServiceTest(ApplicationContext context) {
		nodeInfoService = context.getBean("nodeInfoService", NodeInfoService.class);
		witnessProductBlockService = context.getBean(WitnessProductBlockService.class);
	}

	public void test() {
		BlockCapsule blockCapsule1 = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
				100, ByteString.EMPTY);
		BlockCapsule blockCapsule2 = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
				200, ByteString.EMPTY);
		witnessProductBlockService.validWitnessProductTwoBlock(blockCapsule1);
		witnessProductBlockService.validWitnessProductTwoBlock(blockCapsule2);
		NodeInfo nodeInfo = nodeInfoService.getNodeInfo();
		Assert.assertEquals(nodeInfo.getConfigNodeInfo().getCodeVersion(), Version.getVersion());
		Assert.assertEquals(nodeInfo.getCheatWitnessInfoMap().size(), 1);
		logger.info("{}", JSON.toJSONString(nodeInfo));
	}

	public void testGrpc() {
		WalletBlockingStub walletStub = WalletGrpc
				.newBlockingStub(ManagedChannelBuilder.forTarget(fullnode)
						.usePlaintext(true)
						.build());
		logger.info("getNodeInfo: {}", walletStub.getNodeInfo(EmptyMessage.getDefaultInstance()));
	}

}
