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
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.midasprotocol.protos.Protocol.TransactionInfo.code.FAILED;

@Slf4j
public class ContractTrcToken014 {

	private static final long now = System.currentTimeMillis();
	private static final long TotalSupply = 1000L;
	private static String tokenName = "testAssetIssue_" + now;
	private static long assetAccountId = 0;
	private static long assetAccountUser = 0;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);
	private long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");
	private byte[] transferTokenContractAddress = null;
	private byte[] resultContractAddress = null;

	private String description = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetDescription");
	private String url = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetUrl");

	private ECKey ecKey1 = new ECKey(Utils.getRandom());
	private byte[] dev001Address = ecKey1.getAddress();
	private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

	private ECKey ecKey2 = new ECKey(Utils.getRandom());
	private byte[] user001Address = ecKey2.getAddress();
	private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

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
		PublicMethed.printAddress(user001Key);
	}

	@Test(enabled = true, description = "TransferToken with tokenId in exception condition,"
			+ " deploy transfer contract")
	public void test01DeployTransferTokenContract() {
		Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 1100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.sendcoin(user001Address, 1100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 170000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress, 10_000_000L,
				0, 0, ByteString.copyFrom(dev001Address),
				testKey002, blockingStubFull));

		long start = System.currentTimeMillis() + 2000;
		long end = System.currentTimeMillis() + 1000000000;
		//dev Create a new AssetIssue success.
		Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply,
				1, 10000, start, end, 1, description, url, 100000L,
				100000L, 1L, 1L, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		assetAccountId = PublicMethed.queryAccount(
				dev001Address, blockingStubFull).getAssetIssuedId();
		logger.info("The assetAccountId token name: " + tokenName);
		logger.info("The assetAccountId token ID: " + assetAccountId);

		start = System.currentTimeMillis() + 2000;
		end = System.currentTimeMillis() + 1000000000;
		//user Create a new AssetIssue success.
		Assert.assertTrue(PublicMethed.createAssetIssue(user001Address, tokenName, TotalSupply,
				1, 10000, start, end, 1, description, url, 100000L,
				100000L, 1L, 1L, user001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		assetAccountUser = PublicMethed.queryAccount(
				user001Address, blockingStubFull).getAssetIssuedId();
		logger.info("The assetAccountUser token name: " + tokenName);
		logger.info("The assetAccountUser token ID: " + assetAccountUser);

		//before deploy, check account resource
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long energyLimit = accountResource.getEnergyLimit();
		long energyUsage = accountResource.getEnergyUsed();
		long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountBefore = PublicMethed.getAssetIssueValue(
				dev001Address, assetAccountId, blockingStubFull);

		logger.info("before energyLimit is " + energyLimit);
		logger.info("before energyUsage is " + energyUsage);
		logger.info("before balanceBefore is " + balanceBefore);
		logger.info("before AssetId: " + assetAccountId + ", devAssetCountBefore: " + devAssetCountBefore);

		String contractName = "transferTokenContract";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken014_transferTokenContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken014_transferTokenContract");
		String transferTokenTxid = PublicMethed
				.deployContractAndGetTransactionInfoById(contractName, abi, code, "",
						maxFeeLimit, 0L, 0, 10000,
						assetAccountId, 100, null, dev001Key,
						dev001Address, blockingStubFull);
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

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountAfter = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

		logger.info("after energyLimit is " + energyLimit);
		logger.info("after energyUsage is " + energyUsage);
		logger.info("after balanceAfter is " + balanceAfter);
		logger.info("after AssetId: " + assetAccountId + ", devAssetCountAfter: " + devAssetCountAfter);

		Long contractAssetCount = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("Contract has AssetId: " + assetAccountId + ", Count: "	+ contractAssetCount);

		Assert.assertEquals(Long.valueOf(100), Long.valueOf(devAssetCountBefore - devAssetCountAfter));
		Assert.assertEquals(Long.valueOf(100), contractAssetCount);
	}

	@Test(enabled = true, description = "TransferToken with tokenId in exception condition,"
			+ " deploy receiver contract")
	public void test02DeployRevContract() {
		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// before deploy, check account resource
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long energyLimit = accountResource.getEnergyLimit();
		long energyUsage = accountResource.getEnergyUsed();
		long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountBefore = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

		logger.info("before energyLimit is " + energyLimit);
		logger.info("before energyUsage is " + energyUsage);
		logger.info("before balance is " + balanceBefore);
		logger.info("before AssetId: " + assetAccountId	+ ", devAssetCountBefore: " + devAssetCountBefore);

		String contractName = "resultContract";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken014_resultContract");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken014_resultContract");
		String receiveTokenTxid = PublicMethed
				.deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
						0L, 100, 1000, assetAccountId,
						100, null, dev001Key, dev001Address, blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(receiveTokenTxid, blockingStubFull);

		if (receiveTokenTxid == null || infoById.get().getResultValue() != 0) {
			Assert.fail("deploy receive failed with message: " + infoById.get().getResMessage());
		}

		resultContractAddress = infoById.get().getContractAddress().toByteArray();

		SmartContract smartContract = PublicMethed
				.getContract(resultContractAddress, blockingStubFull);
		Assert.assertNotNull(smartContract.getAbi());

		Long contractAssetCount = PublicMethed.getAssetIssueValue(resultContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("Contract has AssetId: " + assetAccountId + ", Count: " + contractAssetCount);

		// after deploy, check account resource
		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountAfter = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

		logger.info("after energyLimit is " + energyLimit);
		logger.info("after energyUsage is " + energyUsage);
		logger.info("after balanceAfter is " + balanceAfter);
		logger.info("after AssetId: " + assetAccountId + ", devAssetCountAfter: " + devAssetCountAfter);

		Assert.assertEquals(Long.valueOf(100), Long.valueOf(devAssetCountBefore - devAssetCountAfter));
		Assert.assertEquals(Long.valueOf(100), contractAssetCount);
	}

	@Test(enabled = true, description = "TransferToken with tokenId in exception condition,"
			+ " transfer to contract")
	public void test03TriggerContract() {
		Assert.assertTrue(PublicMethed.transferAsset(user001Address,
				assetAccountId, 10L, dev001Address, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long devEnergyLimitBefore = accountResource.getEnergyLimit();
		long devEnergyUsageBefore = accountResource.getEnergyUsed();
		long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

		logger.info("before trigger, devEnergyLimitBefore is " + devEnergyLimitBefore);
		logger.info("before trigger, devEnergyUsageBefore is " + devEnergyUsageBefore);
		logger.info("before trigger, devBalanceBefore is " + devBalanceBefore);

		accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
		long userEnergyLimitBefore = accountResource.getEnergyLimit();
		long userEnergyUsageBefore = accountResource.getEnergyUsed();
		long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
				.getBalance();

		logger.info("before trigger, userEnergyLimitBefore is " + userEnergyLimitBefore);
		logger.info("before trigger, userEnergyUsageBefore is " + userEnergyUsageBefore);
		logger.info("before trigger, userBalanceBefore is " + userBalanceBefore);

		Long transferAssetBefore = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("before trigger, transferTokenContractAddress has AssetId "
				+ assetAccountId + ", Count is " + transferAssetBefore);

		Long receiveAssetBefore = PublicMethed.getAssetIssueValue(resultContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("before trigger, resultContractAddress has AssetId "
				+ assetAccountId + ", Count is " + receiveAssetBefore);

		Long devAssetBefore = PublicMethed.getAssetIssueValue(dev001Address,
				assetAccountId, blockingStubFull);
		logger.info("before trigger, dev001Address has AssetId "
				+ assetAccountId + ", Count is " + devAssetBefore);

		// transfer to smart contract
		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// transfer a not exist TokenId, to a contract
		long tokenId = assetAccountUser;
		long tokenValue = 1L;
		long callValue = 0L;

		String param = "\"" + Base58.encodeBase58(resultContractAddress)
				+ "\",\"" + tokenValue + "\"," + tokenId;

		String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, callValue,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed",
				infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// transfer a not exist TokenId, to a normal account
		tokenId = assetAccountUser;
		tokenValue = 1L;
		callValue = 0L;

		param = "\"" + Base58.encodeBase58(dev001Address) + "\",\"" + tokenValue + "\"," + tokenId;

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, callValue,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed",
				infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenValue is not enough
		tokenId = assetAccountId;
		tokenValue = transferAssetBefore + 100;
		callValue = 0L;

		param = "\"" + Base58.encodeBase58(resultContractAddress)
				+ "\",\"" + tokenValue + "\"," + tokenId;

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, callValue,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenValue is not enough, transfer to a normal account
		tokenId = assetAccountId;
		tokenValue = transferAssetBefore + 100;
		callValue = 0L;

		param = "\"" + Base58.encodeBase58(dev001Address) + "\",\"" + tokenValue + "\"," + tokenId;

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, callValue,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// TokenId is long.max, to a contract
		long fackTokenId = Long.MAX_VALUE;
		long fakeValue = 1L;

		param = "\"" + Base58.encodeBase58(resultContractAddress)
				+ "\"," + fakeValue + ",\"" + fackTokenId + "\"";

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, 0,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// TokenId is not exist, to a contract
		fackTokenId = assetAccountId + 10000;
		fakeValue = 1L;

		param = "\"" + Base58.encodeBase58(resultContractAddress) + "\"," + fakeValue + ",\"" + fackTokenId + "\"";

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, 0,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// TokenId is long.max, to a normal account
		fackTokenId = Long.MAX_VALUE;
		fakeValue = 1L;

		param = "\"" + Base58.encodeBase58(dev001Address)
				+ "\"," + fakeValue + ",\"" + fackTokenId + "\"";

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, 0,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// TokenId is not exist, to a normal account
		fackTokenId = assetAccountId + 10000;
		fakeValue = 1L;

		param = "\"" + Base58.encodeBase58(dev001Address) + "\"," + fakeValue + ",\"" + fackTokenId + "\"";

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, 0,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenValue is long.min, transfer to a contract
		tokenId = assetAccountId;
		tokenValue = Long.MIN_VALUE;
		callValue = 0L;

		param = "\"" + Base58.encodeBase58(resultContractAddress) + "\",\"" + tokenValue + "\"," + tokenId;

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, callValue,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("BigInteger out of long range",
				infoById.get().getResMessage().toStringUtf8());

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(user001Address, user001Key, 50000L,
						blockingStubFull), 0, 1,
				ByteString.copyFrom(user001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenValue is long.min, transfer to a normal account
		param = "\"" + Base58.encodeBase58(dev001Address) + "\",\"" + tokenValue + "\"," + tokenId;

		triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenTest(address,uint256,trcToken)", param, false, callValue,
				1000000000L, assetAccountId, 2, user001Address, user001Key,
				blockingStubFull);

		infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() != 0);
		Assert.assertEquals(FAILED, infoById.get().getResult());
		Assert.assertEquals("BigInteger out of long range",
				infoById.get().getResMessage().toStringUtf8());

		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		long devEnergyLimitAfter = accountResource.getEnergyLimit();
		long devEnergyUsageAfter = accountResource.getEnergyUsed();
		long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

		logger.info("after trigger, devEnergyLimitAfter is " + devEnergyLimitAfter);
		logger.info("after trigger, devEnergyUsageAfter is " + devEnergyUsageAfter);
		logger.info("after trigger, devBalanceAfter is " + devBalanceAfter);

		accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
		long userEnergyLimitAfter = accountResource.getEnergyLimit();
		long userEnergyUsageAfter = accountResource.getEnergyUsed();
		long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
				.getBalance();

		logger.info("after trigger, userEnergyLimitAfter is " + userEnergyLimitAfter);
		logger.info("after trigger, userEnergyUsageAfter is " + userEnergyUsageAfter);
		logger.info("after trigger, userBalanceAfter is " + userBalanceAfter);

		SmartContract smartContract = PublicMethed.getContract(infoById.get().getContractAddress()
				.toByteArray(), blockingStubFull);

		Long transferAssetAfter = PublicMethed.getAssetIssueValue(transferTokenContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("after trigger, transferTokenContractAddress has AssetId "
				+ assetAccountId + ", transferAssetAfter is " + transferAssetAfter);

		Long receiveAssetAfter = PublicMethed.getAssetIssueValue(resultContractAddress,
				assetAccountId, blockingStubFull);
		logger.info("after trigger, resultContractAddress has AssetId "
				+ assetAccountId + ", receiveAssetAfter is " + receiveAssetAfter);

		Long devAssetAfter = PublicMethed.getAssetIssueValue(dev001Address,
				assetAccountId, blockingStubFull);
		logger.info("after trigger, dev001Address has AssetId "
				+ assetAccountId + ", Count is " + devAssetAfter);

		long consumeUserPercent = smartContract.getConsumeUserResourcePercent();
		logger.info("ConsumeURPercent: " + consumeUserPercent);

		Assert.assertEquals(receiveAssetBefore, receiveAssetAfter);
		Assert.assertEquals(devAssetBefore, devAssetAfter);
		Assert.assertEquals(transferAssetBefore, transferAssetAfter);

		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 0,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1,
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


