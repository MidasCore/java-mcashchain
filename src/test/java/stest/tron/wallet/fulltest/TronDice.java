package stest.tron.wallet.fulltest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI.AccountResourceMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.SmartContract;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TronDice {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	byte[] contractAddress;
	Long maxFeeLimit = 1000000000L;
	Optional<TransactionInfo> infoById = null;
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] contract008Address = ecKey1.getAddress();
	String contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ArrayList<String> txidList = new ArrayList<String>();
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
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
		PublicMethed.printAddress(contract008Key);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		PublicMethed.printAddress(testKey002);
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract008Address,
				blockingStubFull);


	}

	@Test(enabled = true, threadPoolSize = 30, invocationCount = 30)
	public void tronDice() {
		ECKey ecKey1 = new ECKey(Utils.getRandom());
		byte[] tronDiceAddress = ecKey1.getAddress();
		String tronDiceKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
		PublicMethed
				.sendcoin(tronDiceAddress, 100000000000L, fromAddress, testKey002, blockingStubFull);
		String contractName = "TronDice";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_TronDice_tronDice");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_TronDice_tronDice");
		byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code, "",
				maxFeeLimit, 1000000000L, 100, null, tronDiceKey, tronDiceAddress, blockingStubFull);
		SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Assert.assertTrue(smartContract.getAbi() != null);

		String txid;

		for (Integer i = 0; i < 100; i++) {
			String initParmes = "\"" + "10" + "\"";
			txid = PublicMethed.triggerContract(contractAddress,
					"rollDice(uint256)", initParmes, false,
					1000000, maxFeeLimit, tronDiceAddress, tronDiceKey, blockingStubFull);
			logger.info(txid);
			txidList.add(txid);

			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Integer successTimes = 0;
		Integer failedTimes = 0;
		Integer totalTimes = 0;
		for (String txid1 : txidList) {
			totalTimes++;
			infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
			if (infoById.get().getBlockNumber() > 3523732) {
				logger.info("blocknum is " + infoById.get().getBlockNumber());
				successTimes++;
			} else {
				failedTimes++;
			}
		}
		logger.info("Total times is " + totalTimes.toString());
		logger.info("success times is " + successTimes.toString());
		logger.info("failed times is " + failedTimes.toString());
		logger.info("success percent is " + successTimes / totalTimes);
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}


