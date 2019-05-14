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
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractScenario013 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	byte[] contractAddress = null;
	String txid = "";
	Optional<TransactionInfo> infoById = null;
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] contract013Address = ecKey1.getAddress();
	String contract013Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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
		PublicMethed.printAddress(contract013Key);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
	}

	@Test(enabled = true)
	public void deployTronTrxAndSunContract() {
		Assert.assertTrue(PublicMethed.sendcoin(contract013Address, 20000000000L, fromAddress,
				testKey002, blockingStubFull));
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract013Address,
				blockingStubFull);
		Long energyLimit = accountResource.getEnergyLimit();
		Long energyUsage = accountResource.getEnergyUsed();

		logger.info("before energy limit is " + Long.toString(energyLimit));
		logger.info("before energy usage is " + Long.toString(energyUsage));
		String contractName = "TronTrxAndSunContract";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractScenario013_deployTronTrxAndSunContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractScenario013_deployTronTrxAndSunContract");
		txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code, "",
				maxFeeLimit, 0L, 100, null, contract013Key, contract013Address, blockingStubFull);
		logger.info(txid);
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() == 0);
		Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() > 0);
		Assert.assertFalse(infoById.get().getContractAddress().isEmpty());
	}

	@Test(enabled = true)
	public void triggerTronTrxAndSunContract() {
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract013Address,
				blockingStubFull);
		Long energyLimit = accountResource.getEnergyLimit();
		Long energyUsage = accountResource.getEnergyUsed();

		logger.info("before energy limit is " + Long.toString(energyLimit));
		logger.info("before energy usage is " + Long.toString(energyUsage));
		String contractName = "TronTrxAndSunContract";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractScenario013_triggerTronTrxAndSunContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractScenario013_triggerTronTrxAndSunContract");
		contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
				0L, 100, null, contract013Key, contract013Address, blockingStubFull);
		txid = PublicMethed.triggerContract(contractAddress,
				"time()", "#", false,
				0, 100000000L, contract013Address, contract013Key, blockingStubFull);
		logger.info(txid);
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		logger.info("result is " + infoById.get().getResultValue());
		Assert.assertTrue(infoById.get().getResultValue() == 0);
		Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() > 0);
		Assert.assertTrue(infoById.get().getFee() == infoById.get().getReceipt().getEnergyFee());
		Assert.assertFalse(infoById.get().getContractAddress().isEmpty());
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


