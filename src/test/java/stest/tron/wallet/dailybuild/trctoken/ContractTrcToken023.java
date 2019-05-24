package stest.tron.wallet.dailybuild.trctoken;

import com.google.protobuf.ByteString;
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
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractTrcToken023 {


	private static final long now = System.currentTimeMillis();
	private static final long TotalSupply = 10000000L;
	private static String tokenName = "testAssetIssue_" + Long.toString(now);
	private static ByteString assetAccountId = null;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	byte[] transferTokenContractAddress;
	byte[] btestAddress;
	String description = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetDescription");
	String url = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetUrl");
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] dev001Address = ecKey1.getAddress();
	String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] user001Address = ecKey2.getAddress();
	String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private String fullnode1 = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);
	private Long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");

	private static int randomInt(int minInt, int maxInt) {
		return (int) Math.round(Math.random() * (maxInt - minInt) + minInt);
	}

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
	}

	@Test(enabled = true, description = "Fallback function energy limit to prevent fallback attacks")
	public void deploy01TransferTokenContract() {

		Assert
				.assertTrue(PublicMethed.sendcoin(dev001Address, 4048000000L, fromAddress,
						testKey002, blockingStubFull));
		logger.info(
				"dev001Address:" + Base58.encodeBase58(dev001Address));
		Assert
				.assertTrue(PublicMethed.sendcoin(user001Address, 4048000000L, fromAddress,
						testKey002, blockingStubFull));
		logger.info(
				"user001Address:" + Base58.encodeBase58(user001Address));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// freeze balance
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(dev001Address, 204800000,
				0, 1, dev001Key, blockingStubFull));

		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(user001Address, 2048000000,
				0, 1, user001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		long start = System.currentTimeMillis() + 2000;
		long end = System.currentTimeMillis() + 1000000000;

		//Create a new AssetIssue success.
		Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
				10000, start, end, 1, description, url, 100000L,
				100000L, 1L, 1L, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		int i = randomInt(6666666, 9999999);
		ByteString tokenId1 = ByteString.copyFromUtf8(String.valueOf(i));
		assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();

		// deploy transferTokenContract
		int originEnergyLimit = 50000;
		String contractName2 = "tokenTest";
		String code2 = Configuration.getByPath("testng.conf")
				.getString("code.code2_ContractTrcToken023_tokenTest");
		String abi2 = Configuration.getByPath("testng.conf")
				.getString("abi.abi2_ContractTrcToken023_tokenTest");
		transferTokenContractAddress = PublicMethed
				.deployContract(contractName2, abi2, code2, "", maxFeeLimit,
						0L, 0, originEnergyLimit, "0",
						0, null, dev001Key, dev001Address,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String contractName = "BTest";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken023_tokenTest");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken023_tokenTest");
		btestAddress = PublicMethed
				.deployContract(contractName, abi, code, "", maxFeeLimit,
						0L, 0, originEnergyLimit, "0",
						0, null, dev001Key, dev001Address,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Assert
				.assertTrue(PublicMethed.sendcoin(transferTokenContractAddress, 1000000000L, fromAddress,
						testKey002, blockingStubFull));
		Assert
				.assertTrue(PublicMethed.sendcoin(btestAddress, 1000000000L, fromAddress,
						testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// devAddress transfer token to userAddress
		PublicMethed
				.transferAsset(transferTokenContractAddress, assetAccountId.toByteArray(), 100,
						dev001Address,
						dev001Key,
						blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);
	}

	@Test(enabled = true, description = "Fallback function energy used >2300")
	public void deploy02TransferTokenContract() {
		Account info;
		AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		info = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		Long beforeBalance = info.getBalance();
		Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
		Long beforeNetUsed = resourceInfo.getNetUsed();
		Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
		Long beforeAssetIssueDevAddress = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueUserAddress = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);

		Long beforeAssetIssueContractAddress = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueBAddress = PublicMethed
				.getAssetIssueValue(btestAddress, assetAccountId,
						blockingStubFull);

		Long beforeBalanceContractAddress = PublicMethed.queryAccount(transferTokenContractAddress,
				blockingStubFull).getBalance();
		Long beforeUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
				.getBalance();
		logger.info("beforeBalance:" + beforeBalance);
		logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
		logger.info("beforeNetUsed:" + beforeNetUsed);
		logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
		logger.info("beforeAssetIssueContractAddress:" + beforeAssetIssueContractAddress);
		logger.info("beforeAssetIssueBAddress:" + beforeAssetIssueBAddress);

		logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress);
		logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress);
		logger.info("beforeBalanceContractAddress:" + beforeBalanceContractAddress);
		logger.info("beforeUserBalance:" + beforeUserBalance);

		String param =
				"\"" + Base58.encodeBase58(btestAddress) + "\",\"" + assetAccountId
						.toStringUtf8()
						+ "\",\"1\"";

		final String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"TransferTokenTo(address,trcToken,uint256)",
				param, false, 0, 1000000000L, "0",
				0, dev001Address, dev001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account infoafter = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		Long afterBalance = infoafter.getBalance();
		Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
		Long afterAssetIssueDevAddress = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long afterNetUsed = resourceInfoafter.getNetUsed();
		Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
		Long afterAssetIssueContractAddress = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);
		Long afterAssetIssueBAddress = PublicMethed
				.getAssetIssueValue(btestAddress, assetAccountId,
						blockingStubFull);

		Long afterAssetIssueUserAddress = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long afterBalanceContractAddress = PublicMethed.queryAccount(transferTokenContractAddress,
				blockingStubFull).getBalance();
		Long afterUserBalance = PublicMethed.queryAccount(user001Address, blockingStubFull)
				.getBalance();

		logger.info("afterBalance:" + afterBalance);
		logger.info("afterEnergyUsed:" + afterEnergyUsed);
		logger.info("afterNetUsed:" + afterNetUsed);
		logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
		logger.info("afterAssetIssueCount:" + afterAssetIssueDevAddress);
		logger.info("afterAssetIssueDevAddress:" + afterAssetIssueContractAddress);
		logger.info("afterAssetIssueBAddress:" + afterAssetIssueBAddress);
		logger.info("afterAssetIssueUserAddress:" + afterAssetIssueUserAddress);
		logger.info("afterBalanceContractAddress:" + afterBalanceContractAddress);
		logger.info("afterUserBalance:" + afterUserBalance);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() == 1);
		Assert.assertTrue(afterAssetIssueUserAddress == beforeAssetIssueUserAddress);
		Assert.assertEquals(afterBalanceContractAddress, beforeBalanceContractAddress);
		Assert.assertTrue(afterAssetIssueContractAddress == beforeAssetIssueContractAddress);
		Assert.assertTrue(afterAssetIssueBAddress == beforeAssetIssueBAddress);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
				dev001Address, blockingStubFull);

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


