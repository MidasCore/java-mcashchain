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
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractTrcToken048 {

	private static final long TotalSupply = 10000000L;
	private static final long now = System.currentTimeMillis();
	private static String tokenName = "testAssetIssue_" + Long.toString(now);
	private static ByteString assetAccountId = null;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
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


	@Test(enabled = true, description = "TransferToken msg.value msg,tokenvalue is negative number")
	public void deployTransferTokenContract() {

		Assert
				.assertTrue(PublicMethed.sendcoin(dev001Address, 2048000000, fromAddress,
						testKey002, blockingStubFull));
		Assert
				.assertTrue(PublicMethed.sendcoin(user001Address, 4048000000L, fromAddress,
						testKey002, blockingStubFull));
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
				100, start, end, 1, description, url, 10000L,
				10000L, 1L, 1L, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();
		ByteString assetAccountDev = PublicMethed
				.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();
		final ByteString fakeassetAccountId = ByteString
				.copyFromUtf8(Long.toString(Long.valueOf(assetAccountDev.toStringUtf8()) + 100));

		// devAddress transfer token to A
		PublicMethed.transferAsset(dev001Address, assetAccountId.toByteArray(), 101, user001Address,
				user001Key, blockingStubFull);

		// deploy transferTokenContract
		String contractName = "transferTokenContract";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken048_transferTokenContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken048_transferTokenContract");
		byte[] transferTokenContractAddress = PublicMethed
				.deployContract(contractName, abi, code, "", maxFeeLimit,
						0L, 100, 10000, "0",
						0, null, dev001Key, dev001Address,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// devAddress transfer token to userAddress
		PublicMethed
				.transferAsset(transferTokenContractAddress, assetAccountId.toByteArray(), 100,
						user001Address,
						user001Key,
						blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Account info;
		AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(user001Address,
				blockingStubFull);
		info = PublicMethed.queryAccount(user001Address, blockingStubFull);
		Long beforeBalance = info.getBalance();
		Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
		Long beforeNetUsed = resourceInfo.getNetUsed();
		Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
		Long beforeAssetIssueCount = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueContractAddress = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);

		logger.info("beforeBalance:" + beforeBalance);
		logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
		logger.info("beforeNetUsed:" + beforeNetUsed);
		logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
		logger.info("beforeAssetIssueCount:" + beforeAssetIssueCount);
		logger.info("beforeAssetIssueContractAddress:" + beforeAssetIssueContractAddress);

		// user trigger A to transfer token to B

		final String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"testMsgTokenValue()",
				"#", false, 0, 100000000L, fakeassetAccountId.toStringUtf8(),
				-1000, user001Address, user001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String triggerTxid1 = PublicMethed.triggerContract(transferTokenContractAddress,
				"testMsgValue()",
				"#", false, -1000, 100000000L, "0",
				0, user001Address, user001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account infoafter = PublicMethed.queryAccount(user001Address, blockingStubFull);
		AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(user001Address,
				blockingStubFull);
		Long afterBalance = infoafter.getBalance();
		Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
		Long afterAssetIssueCount = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long afterNetUsed = resourceInfoafter.getNetUsed();
		Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
		Long afterAssetIssueContractAddress = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);

		logger.info("afterBalance:" + afterBalance);
		logger.info("afterEnergyUsed:" + afterEnergyUsed);
		logger.info("afterNetUsed:" + afterNetUsed);
		logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
		logger.info("afterAssetIssueCount:" + afterAssetIssueCount);
		logger.info("afterAssetIssueContractAddress:" + afterAssetIssueContractAddress);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertEquals(beforeBalance, afterBalance);
		Assert.assertEquals(beforeAssetIssueCount, afterAssetIssueCount);
		Assert.assertEquals(beforeAssetIssueContractAddress, afterAssetIssueContractAddress);
		PublicMethed.unFreezeBalance(dev001Address, dev001Key, 1,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(user001Address, user001Key, 1,
				user001Address, blockingStubFull);
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


