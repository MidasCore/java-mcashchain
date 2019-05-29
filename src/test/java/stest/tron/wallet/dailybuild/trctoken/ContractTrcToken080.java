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
import io.midasprotocol.protos.Protocol.SmartContract;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractTrcToken080 {

	private static final long now = System.currentTimeMillis();
	private static final long TotalSupply = 1000L;
	private static String tokenName = "testAssetIssue_" + now;
	private static long assetAccountId = 0;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");
	private byte[] transferTokenContractAddress = null;

	private String description = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetDescription");
	private String url = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetUrl");

	private ECKey ecKey1 = new ECKey(Utils.getRandom());
	private byte[] dev001Address = ecKey1.getAddress();
	private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

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

		PublicMethed.printAddress(dev001Key);
	}

	@Test(enabled = true, description = "DeployContract with 0 tokenValue and tokenId")
	public void deployTransferTokenContract() {
		Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 1100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 130000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress, 10_000_000L,
				0, 0, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		long start = System.currentTimeMillis() + 2000;
		long end = System.currentTimeMillis() + 1000000000;
		//Create a new AssetIssue success.
		Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
				10000, start, end, 1, description, url, 100000L, 100000L,
				1L, 1L, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedId();
		logger.info("The token name: " + tokenName);
		logger.info("The token ID: " + assetAccountId);

		//before deploy, check account resource
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long energyLimit = accountResource.getEnergyLimit();
		long energyUsage = accountResource.getEnergyUsed();
		long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountBefore = PublicMethed.getAssetIssueValue(dev001Address,
				assetAccountId, blockingStubFull);

		logger.info("before energyLimit is " + energyLimit);
		logger.info("before energyUsage is " + energyUsage);
		logger.info("before balanceBefore is " + balanceBefore);
		logger.info("before AssetId: " + assetAccountId + ", devAssetCountBefore: " + devAssetCountBefore);

		String contractName = "transferTokenContract";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken0612_transferTokenContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken0612_transferTokenContract");
		long tokenId = 0;
		long tokenValue = 0;
		long callValue = 10;

		String transferTokenTxid = PublicMethed
				.deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
						callValue, 0, 10000, tokenId, tokenValue,
						null, dev001Key, dev001Address, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(transferTokenTxid, blockingStubFull);

		if (transferTokenTxid == null || infoById.get().getResultValue() != 0) {
			Assert.fail("deploy transaction failed with message: " + infoById.get().getResMessage());
		}

		transferTokenContractAddress = infoById.get().getContractAddress().toByteArray();
		SmartContract smartContract = PublicMethed.getContract(transferTokenContractAddress,
				blockingStubFull);
		Assert.assertNotNull(smartContract.getAbi());

		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountAfter = PublicMethed.getAssetIssueValue(dev001Address,
				assetAccountId, blockingStubFull);

		logger.info("after energyLimit is " + energyLimit);
		logger.info("after energyUsage is " + energyUsage);
		logger.info("after balanceAfter is " + balanceAfter);
		logger.info("after AssetId: " + assetAccountId + ", devAssetCountAfter: " + devAssetCountAfter);

		Assert.assertTrue(PublicMethed.transferAsset(transferTokenContractAddress,
				assetAccountId, 100L, dev001Address, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Long contractAssetCount = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("Contract has AssetId: " + assetAccountId + ", Count: " + contractAssetCount);

		Assert.assertEquals(Long.valueOf(tokenValue),
				Long.valueOf(devAssetCountBefore - devAssetCountAfter));
		Assert.assertEquals(Long.valueOf(100L + tokenValue), contractAssetCount);

		// get and verify the msg.value and msg.id
		Long transferAssetBefore = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("before trigger, transferTokenContractAddress has AssetId "
				+ assetAccountId + ", Count is " + transferAssetBefore);

		String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"getResultInCon()", "#", false, 0,
				1000000000L, 0, 0, dev001Address, dev001Key,
				blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		if (infoById.get().getResultValue() != 0) {
			Assert.fail("transaction failed with message: " + infoById.get().getResMessage());
		}

		logger.info("The msg value: " + PublicMethed.getStrings(infoById.get()
				.getContractResult(0).toByteArray()));

		List<String> retList = PublicMethed.getStrings(infoById.get()
				.getContractResult(0).toByteArray());

		Long msgId = ByteArray.toLong(ByteArray.fromHexString(retList.get(0)));
		Long msgTokenValue = ByteArray.toLong(ByteArray.fromHexString(retList.get(1)));
		Long msgCallValue = ByteArray.toLong(ByteArray.fromHexString(retList.get(2)));

		logger.info("msgId: " + msgId);
		logger.info("msgTokenValue: " + msgTokenValue);
		logger.info("msgCallValue: " + msgCallValue);

		Assert.assertEquals(msgId.toString(), tokenId);
		Assert.assertEquals(Long.valueOf(msgTokenValue), Long.valueOf(tokenValue));
		Assert.assertEquals(Long.valueOf(msgCallValue), Long.valueOf(callValue));

		// unfreeze resource
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 0,
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


