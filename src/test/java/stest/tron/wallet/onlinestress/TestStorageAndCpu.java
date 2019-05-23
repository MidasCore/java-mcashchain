package stest.tron.wallet.onlinestress;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.EmptyMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.Block;
import io.midasprotocol.protos.Protocol.ChainParameters;
import io.midasprotocol.protos.Protocol.SmartContract;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TestStorageAndCpu {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("witness.key5");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);

	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("witness.key4");
	private final byte[] testAddress003 = PublicMethed.getFinalAddress(testKey003);

	private final String testKey004 = Configuration.getByPath("testng.conf")
			.getString("witness.key3");
	private final byte[] testAddress004 = PublicMethed.getFinalAddress(testKey004);
	ArrayList<String> txidList = new ArrayList<String>();
	Optional<TransactionInfo> infoById = null;
	Long beforeTime;
	Long afterTime;
	Long beforeBlockNum;
	Long afterBlockNum;
	Block currentBlock;
	Long currentBlockNum;
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private ManagedChannel channelFull1 = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private String fullnode1 = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);

	@BeforeSuite
	public void beforeSuite() {
		Wallet wallet = new Wallet();
	}

	/**
	 * constructor.
	 */

	@BeforeClass(enabled = true)
	public void beforeClass() {
		PublicMethed.printAddress(testKey002);
		PublicMethed.printAddress(testKey003);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
		currentBlock = blockingStubFull1.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
		beforeBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
		beforeTime = System.currentTimeMillis();
	}

	@Test(enabled = true, threadPoolSize = 1, invocationCount = 1)
	public void storageAndCpu() {
		Random rand = new Random();
		Integer randNum = rand.nextInt(30) + 1;
		randNum = rand.nextInt(4000);

		Long maxFeeLimit = 1000000000L;
		String contractName = "StorageAndCpu" + Integer.toString(randNum);
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_TestStorageAndCpu_storageAndCpu");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_TestStorageAndCpu_storageAndCpu");
		PublicMethed
				.freezeBalanceGetEnergy(fromAddress, 1000000000000L, 3, 1, testKey002, blockingStubFull);
		byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code,
				"", maxFeeLimit,
				0L, 100, null, testKey002, fromAddress, blockingStubFull);
		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
		String txid;

		ChainParameters chainParameters = blockingStubFull
				.getChainParameters(EmptyMessage.newBuilder().build());
		Optional<ChainParameters> getChainParameters = Optional.ofNullable(chainParameters);

		Integer i = 1;
		while (i++ < 8000) {
			String initParmes = "\"" + "930" + "\"";
			txid = PublicMethed.triggerContract(contractAddress,
					"testUseCpu(uint256)", "9100", false,
					0, maxFeeLimit, fromAddress, testKey002, blockingStubFull);
			txid = PublicMethed.triggerContract(contractAddress,
					"storage8Char()", "", false,
					0, maxFeeLimit, fromAddress, testKey002, blockingStubFull);
			//storage 9 EnergyUsageTotal is  211533, 10 is 236674, 5 is 110969,21 is 500000
			txid = PublicMethed.triggerContract(contractAddress,
					"testUseStorage(uint256)", "21", false,
					0, maxFeeLimit, fromAddress, testKey002, blockingStubFull);
			//logger.info("i is " +Integer.toString(i) + " " + txid);
			//txidList.add(txid);
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (i % 10 == 0) {
				chainParameters = blockingStubFull
						.getChainParameters(EmptyMessage.newBuilder().build());
				getChainParameters = Optional.ofNullable(chainParameters);
				logger.info(getChainParameters.get().getChainParameter(22).getKey());
				logger.info(Long.toString(getChainParameters.get().getChainParameter(22).getValue()));
				logger.info(getChainParameters.get().getChainParameter(23).getKey());
				logger.info(Long.toString(getChainParameters.get().getChainParameter(23).getValue()));

			}
		}
	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
    /*
    afterTime = System.currentTimeMillis();
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    currentBlock = blockingStubFull1.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    afterBlockNum = currentBlock.getBlockHeader().getRawData().getNumber() + 2;
    Long blockNum = beforeBlockNum;
    Integer txsNum = 0;
    Integer topNum = 0;
    Integer totalNum = 0;
    Long energyTotal = 0L;
    String findOneTxid = "";

    NumberMessage.Builder builder = NumberMessage.newBuilder();
    while (blockNum <= afterBlockNum) {
      builder.setNum(blockNum);
      txsNum = blockingStubFull1.getBlockByNum(builder.build()).getTransactionsCount();
      totalNum = totalNum + txsNum;
      if (topNum < txsNum) {
        topNum = txsNum;
        findOneTxid = ByteArray.toHexString(Sha256Hash.hash(blockingStubFull1
            .getBlockByNum(builder.build()).getTransactionsList().get(2)
            .getRawData().toByteArray()));
        //logger.info("find one txid is " + findOneTxid);
      }

      blockNum++;
    }
    Long costTime = (afterTime - beforeTime - 31000) / 1000;
    logger.info("Duration block num is  " + (afterBlockNum - beforeBlockNum - 11));
    logger.info("Cost time are " + costTime);
    logger.info("Top block txs num is " + topNum);
    logger.info("Total transaction is " + (totalNum - 30));
    logger.info("Average Tps is " + (totalNum / costTime));

    infoById = PublicMethed.getTransactionInfoById(findOneTxid, blockingStubFull1);
    Long oneEnergyTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("EnergyTotal is " + oneEnergyTotal);
    logger.info("Average energy is " + oneEnergyTotal * (totalNum / costTime));
*/

		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (channelFull1 != null) {
			channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}