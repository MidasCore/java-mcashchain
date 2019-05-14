package stest.tron.wallet.contract.scenario;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.AccountResourceMessage;
import org.tron.api.WalletGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.SmartContract;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractScenario009 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] contract009Address = ecKey1.getAddress();
	String contract009Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
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
		PublicMethed.printAddress(contract009Key);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
	}

	@Test(enabled = true)
	public void deployContainLibraryContract() {
		Assert.assertTrue(PublicMethed.sendcoin(contract009Address, 20000000L, fromAddress,
				testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(contract009Address, 1000000L,
				3, 1, contract009Key, blockingStubFull));
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract009Address,
				blockingStubFull);
		Long energyLimit = accountResource.getEnergyLimit();
		Long energyUsage = accountResource.getEnergyUsed();

		logger.info("before energy limit is " + Long.toString(energyLimit));
		logger.info("before energy usage is " + Long.toString(energyUsage));
		String contractName = "Library";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractScenario009_deployContainLibraryContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractScenario009_deployContainLibraryContract");
		byte[] libraryAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
				0L, 100, null, contract009Key, contract009Address, blockingStubFull);
		SmartContract smartContract = PublicMethed.getContract(libraryAddress, blockingStubFull);

		Assert.assertFalse(smartContract.getAbi().toString().isEmpty());
		Assert.assertTrue(smartContract.getName().equalsIgnoreCase(contractName));
		Assert.assertFalse(smartContract.getBytecode().toString().isEmpty());
		logger.info(ByteArray.toHexString(smartContract.getContractAddress().toByteArray()));
		accountResource = PublicMethed.getAccountResource(contract009Address, blockingStubFull);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		Assert.assertTrue(energyLimit > 0);
		Assert.assertTrue(energyUsage > 0);

		logger.info("after energy limit is " + Long.toString(energyLimit));
		logger.info("after energy usage is " + Long.toString(energyUsage));
	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}


