package stest.tron.wallet.contract.scenario;

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
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractScenario001 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] contract001Address = ecKey1.getAddress();
	String contract001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private ManagedChannel channelFull1 = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);
	private String fullnode1 = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private Long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");

	@BeforeSuite
	public void beforeSuite() {
		Wallet wallet = new Wallet();
	}

	/**
	 * constructor.
	 */

	@BeforeClass(enabled = true)
	public void beforeClass() {
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
				.usePlaintext(true)
				.build();
		blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
	}

	@Test(enabled = true)
	public void deployAddressDemo() {
		ecKey1 = new ECKey(Utils.getRandom());
		contract001Address = ecKey1.getAddress();
		contract001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
		PublicMethed.printAddress(contract001Key);

		Assert.assertTrue(PublicMethed.sendcoin(contract001Address, 20000000L, toAddress,
				testKey003, blockingStubFull));
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(contract001Address, 10000000L,
				3, 1, contract001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull1);
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract001Address,
				blockingStubFull);
		Long energyLimit = accountResource.getEnergyLimit();
		Long energyUsage = accountResource.getEnergyUsed();
		Long balanceBefore = PublicMethed.queryAccount(contract001Key, blockingStubFull).getBalance();

		logger.info("before energy limit is " + Long.toString(energyLimit));
		logger.info("before energy usage is " + Long.toString(energyUsage));
		logger.info("before balance is " + Long.toString(balanceBefore));

		String contractName = "addressDemo";
		String code = "608060405234801561001057600080fd5b5060bf8061001f6000396000f3006080604052600436"
				+ "1060485763ffffffff7c0100000000000000000000000000000000000000000000000000000000600035041"
				+ "66313d1aa2e8114604d5780637995b15b146067575b600080fd5b348015605857600080fd5b506065600435"
				+ "602435608b565b005b348015607257600080fd5b506079608f565b60408051918252519081900360200190f"
				+ "35b5050565b42905600a165627a7a72305820086db30620ef850edcb987d91625ecf5a1c342dc87dbabb4fe"
				+ "4b29ec8c1623c10029";
		String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"start\",\"type\":\"uint256\"},{\"na"
				+ "me\":\"daysAfter\",\"type\":\"uint256\"}],\"name\":\"f\",\"outputs\":[],\"payable\":fal"
				+ "se,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inpu"
				+ "ts\":[],\"name\":\"nowInSeconds\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\""
				+ "payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
		byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
				0L, 100, null, contract001Key, contract001Address, blockingStubFull);
		SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
		Assert.assertTrue(smartContract.getAbi() != null);

		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull1);
		accountResource = PublicMethed.getAccountResource(contract001Address, blockingStubFull1);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		Long balanceAfter = PublicMethed.queryAccount(contract001Key, blockingStubFull1).getBalance();

		logger.info("after energy limit is " + Long.toString(energyLimit));
		logger.info("after energy usage is " + Long.toString(energyUsage));
		logger.info("after balance is " + Long.toString(balanceAfter));

		Assert.assertTrue(energyLimit > 0);
		Assert.assertTrue(energyUsage > 0);
		Assert.assertEquals(balanceBefore, balanceAfter);
	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (channelFull1 != null) {
			channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}


