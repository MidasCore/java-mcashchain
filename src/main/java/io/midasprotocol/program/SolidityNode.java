package io.midasprotocol.program;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.util.StringUtils;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.overlay.client.DatabaseGrpcClient;
import io.midasprotocol.common.overlay.discover.DiscoverServer;
import io.midasprotocol.common.overlay.discover.node.NodeManager;
import io.midasprotocol.common.overlay.server.ChannelManager;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.services.RpcApiService;
import io.midasprotocol.core.services.http.solidity.SolidityNodeHttpApiService;
import io.midasprotocol.protos.Protocol.Block;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

import static io.midasprotocol.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

@Slf4j(topic = "app")
public class SolidityNode {

	private Manager dbManager;

	private DatabaseGrpcClient databaseGrpcClient;

	private AtomicLong id = new AtomicLong();

	private AtomicLong remoteBlockNum = new AtomicLong();

	private LinkedBlockingDeque<Block> blockQueue = new LinkedBlockingDeque<>(100);

	private int exceptionSleepTime = 1000;

	private volatile boolean flag = true;

	public SolidityNode(Manager dbManager) {
		this.dbManager = dbManager;
		resolveCompatibilityIssueIfUsingFullNodeDatabase();
		id.set(dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
		databaseGrpcClient = new DatabaseGrpcClient(Args.getInstance().getTrustNodeAddr());
		remoteBlockNum.set(getLastSolidityBlockNum());
	}

	/**
	 * Start the SolidityNode.
	 */
	public static void main(String[] args) {
		logger.info("Solidity node running.");
		Args.setParam(args, Constant.TESTNET_CONF);
		Args cfgArgs = Args.getInstance();

		logger.info("index switch is {}",
				BooleanUtils.toStringOnOff(BooleanUtils.toBoolean(cfgArgs.getStorage().getIndexSwitch())));

		if (StringUtils.isEmpty(cfgArgs.getTrustNodeAddr())) {
			logger.error("Trust node not set.");
			return;
		}
		cfgArgs.setSolidityNode(true);

		org.springframework.context.ApplicationContext context = new ApplicationContext(DefaultConfig.class);

		if (cfgArgs.isHelp()) {
			logger.info("Here is the help message.");
			return;
		}
		Application appT = ApplicationFactory.create(context);
		FullNode.shutdown(appT);

		//appT.init(cfgArgs);
		RpcApiService rpcApiService = context.getBean(RpcApiService.class);
		appT.addService(rpcApiService);
		//http
		SolidityNodeHttpApiService httpApiService = context.getBean(SolidityNodeHttpApiService.class);
		appT.addService(httpApiService);

		appT.initServices(cfgArgs);
		appT.startServices();
//    	appT.startup();

		//Disable peer discovery for solidity node
		DiscoverServer discoverServer = context.getBean(DiscoverServer.class);
		discoverServer.close();
		ChannelManager channelManager = context.getBean(ChannelManager.class);
		channelManager.close();
		NodeManager nodeManager = context.getBean(NodeManager.class);
		nodeManager.close();

		SolidityNode node = new SolidityNode(appT.getDbManager());
		node.start();

		rpcApiService.blockUntilShutdown();
	}

	private void start() {
		try {
			new Thread(this::getBlock).start();
			new Thread(this::processBlock).start();
			logger.info("Success to start solid node, id: {}, remoteBlockNum: {}.", id.get(),
					remoteBlockNum);
		} catch (Exception e) {
			logger.error("Failed to start solid node, address: {}.", Args.getInstance().getTrustNodeAddr());
			System.exit(0);
		}
	}

	private void getBlock() {
		long blockNum = id.incrementAndGet();
		while (flag) {
			try {
				if (blockNum > remoteBlockNum.get()) {
					sleep(BLOCK_PRODUCED_INTERVAL);
					remoteBlockNum.set(getLastSolidityBlockNum());
					continue;
				}
				Block block = getBlockByNum(blockNum);
				blockQueue.put(block);
				blockNum = id.incrementAndGet();
			} catch (Exception e) {
				logger.error("Failed to get block {}, reason: {}.", blockNum, e.getMessage());
				sleep(exceptionSleepTime);
			}
		}
	}

	private void processBlock() {
		while (flag) {
			try {
				Block block = blockQueue.take();
				loopProcessBlock(block);
			} catch (Exception e) {
				logger.error(e.getMessage());
				sleep(exceptionSleepTime);
			}
		}
	}

	private void loopProcessBlock(Block block) {
		while (flag) {
			long blockNum = block.getBlockHeader().getRawData().getNumber();
			try {
				dbManager.pushVerifiedBlock(new BlockCapsule(block));
				dbManager.getDynamicPropertiesStore().saveLatestSolidifiedBlockNum(blockNum);
				logger.info("Success to process block: {}, blockQueueSize: {}.", blockNum, blockQueue.size());
				return;
			} catch (Exception e) {
				logger.error("Failed to process block {}.", new BlockCapsule(block), e);
				sleep(exceptionSleepTime);
				block = getBlockByNum(blockNum);
			}
		}
	}

	private Block getBlockByNum(long blockNum) {
		while (true) {
			try {
				long time = System.currentTimeMillis();
				Block block = databaseGrpcClient.getBlock(blockNum);
				long num = block.getBlockHeader().getRawData().getNumber();
				if (num == blockNum) {
					logger.info("Success to get block: {}, cost: {}ms.",
							blockNum, System.currentTimeMillis() - time);
					return block;
				} else {
					logger.warn("Get block id not the same , {}, {}.", num, blockNum);
					sleep(exceptionSleepTime);
				}
			} catch (Exception e) {
				logger.error("Failed to get block: {}, reason: {}.", blockNum, e.getMessage());
				sleep(exceptionSleepTime);
			}
		}
	}

	private long getLastSolidityBlockNum() {
		while (true) {
			try {
				long time = System.currentTimeMillis();
				long blockNum = databaseGrpcClient.getDynamicProperties().getLastSolidityBlockNum();
				logger.info("Get last remote solid blockNum: {}, remoteBlockNum: {}, cost: {}.",
						blockNum, remoteBlockNum, System.currentTimeMillis() - time);
				return blockNum;
			} catch (Exception e) {
				logger.error("Failed to get last solid blockNum: {}, reason: {}.", remoteBlockNum.get(),
						e.getMessage());
				sleep(exceptionSleepTime);
			}
		}
	}

	public void sleep(long time) {
		try {
			Thread.sleep(time);
		} catch (Exception ignored) {
		}
	}

	private void resolveCompatibilityIssueIfUsingFullNodeDatabase() {
		long lastSolidityBlockNum = dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
		long headBlockNum = dbManager.getHeadBlockNum();
		logger.info("headBlockNum:{}, solidityBlockNum:{}, diff:{}",
				headBlockNum, lastSolidityBlockNum, headBlockNum - lastSolidityBlockNum);
		if (lastSolidityBlockNum < headBlockNum) {
			logger.info("use fullNode database, headBlockNum:{}, solidityBlockNum:{}, diff:{}",
					headBlockNum, lastSolidityBlockNum, headBlockNum - lastSolidityBlockNum);
			dbManager.getDynamicPropertiesStore().saveLatestSolidifiedBlockNum(headBlockNum);
		}
	}
}