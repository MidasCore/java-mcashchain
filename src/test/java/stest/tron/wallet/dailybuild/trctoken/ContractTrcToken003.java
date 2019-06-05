package stest.tron.wallet.dailybuild.trctoken;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.AccountResourceMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.concurrent.TimeUnit;

import static io.midasprotocol.api.GrpcAPI.Return.ResponseCode.CONTRACT_VALIDATE_ERROR;


@Slf4j
public class ContractTrcToken003 {

	private static final long now = System.currentTimeMillis();
	private static final long TotalSupply = 1000L;
	private static String tokenName = "testAssetIssue_" + now;
	private static long assetAccountDev = 0;
	private static long assetAccountUser = 0;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");
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

	}

	@Test(enabled = true, description = "DeployContract with exception condition")
	public void deployTransferTokenContract() {
		Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 1100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.sendcoin(user001Address, 1100_000_000L, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress,
				PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key, 50000L, blockingStubFull),
				0, 1, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.freezeBalanceForReceiver(fromAddress, 10_000_000L,
				0, 0, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		long start = System.currentTimeMillis() + 2000;
		long end = System.currentTimeMillis() + 1000000000;
		//dev Create a new AssetIssue
		Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
				10000, start, end, 1, description, url, 100000L, 100000L,
				1L, 1L, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		assetAccountDev = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedId();
		logger.info("The assetAccountDev token name: " + tokenName);
		logger.info("The assetAccountDev token ID: " + assetAccountDev);

		start = System.currentTimeMillis() + 2000;
		end = System.currentTimeMillis() + 1000000000;
		//user Create a new AssetIssue
		Assert.assertTrue(PublicMethed.createAssetIssue(user001Address, tokenName, TotalSupply, 1,
				10000, start, end, 1, description, url, 100000L, 100000L,
				1L, 1L, user001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		assetAccountUser = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getAssetIssuedId();
		logger.info("The assetAccountUser token name: " + tokenName);
		logger.info("The assetAccountUser token ID: " + assetAccountUser);

		//before deploy, check account resource
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		long energyLimit = accountResource.getEnergyLimit();
		long energyUsage = accountResource.getEnergyUsed();
		long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountBefore = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountDev, blockingStubFull);
		Long userAssetCountBefore = PublicMethed.getAssetIssueValue(dev001Address, assetAccountUser,
				blockingStubFull);

		logger.info("before energyLimit is " + energyLimit);
		logger.info("before energyUsage is " + energyUsage);
		logger.info("before balanceBefore is " + balanceBefore);
		logger.info("before dev has AssetId: " + assetAccountDev + ", devAssetCountBefore: " + devAssetCountBefore);
		logger.info("before dev has AssetId: " + assetAccountUser + ", userAssetCountBefore: " + userAssetCountBefore);

		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String contractName = "transferTokenContract";
		String code = "608060405260e2806100126000396000f300608060405260043610603e5763ffffffff7c01000000"
				+ "000000000000000000000000000000000000000000000000006000350416633be9ece781146043575b600080"
				+ "fd5b606873ffffffffffffffffffffffffffffffffffffffff60043516602435604435606a565b005b604051"
				+ "73ffffffffffffffffffffffffffffffffffffffff84169082156108fc029083908590600081818185878a8a"
				+ "d094505050505015801560b0573d6000803e3d6000fd5b505050505600a165627a7a723058200ba246bdb58b"
				+ "e0f221ad07e1b19de843ab541150b329ddd01558c2f1cefe1e270029";
		String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"toAddress\",\"type\":\"address\"},"
				+ "{\"name\":\"id\",\"type\":\"trcToken\"},{\"name\":\"amount\",\"type\":\"uint256\"}],"
				+ "\"name\":\"TransferTokenTo\",\"outputs\":[],\"payable\":true,\"stateMutability\":"
				+ "\"payable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":true,\"stateMutability\":"
				+ "\"payable\",\"type\":\"constructor\"}]";

		// the tokenId is not exist
		long fakeTokenId = assetAccountDev + 100;
		Long fakeTokenValue = 100L;

		GrpcAPI.Return response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, fakeTokenValue, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : No asset !",
				response.getMessage().toStringUtf8());

		// deployer didn't have any such token
		fakeTokenId = assetAccountUser;
		fakeTokenValue = 100L;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, fakeTokenValue, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : assetBalance must greater than 0.",
				response.getMessage().toStringUtf8());

		// deployer didn't have any Long.MAX_VALUE
		fakeTokenId = Long.MAX_VALUE;
		fakeTokenValue = 100L;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, fakeTokenValue, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : No asset !",
				response.getMessage().toStringUtf8());

		// the tokenValue is not enough
		fakeTokenId = assetAccountDev;
		fakeTokenValue = devAssetCountBefore + 100;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, fakeTokenValue, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : assetBalance is not sufficient.",
				response.getMessage().toStringUtf8());

		// tokenid is -1
		fakeTokenId = -1;
		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, 100, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenId must > 1000000",
				response.getMessage().toStringUtf8());

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenid is 100_0000L
		fakeTokenId = 100_0000L;
		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, 100, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenId must > 1000000", response.getMessage().toStringUtf8());

		// tokenid is Long.MIN_VALUE
		fakeTokenId = Long.MIN_VALUE;
		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, 100, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenId must > 1000000",
				response.getMessage().toStringUtf8());

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenid is 0
		fakeTokenId = 0;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				fakeTokenId, 100, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : invalid arguments with tokenValue = 100, tokenId = 0",
				response.getMessage().toStringUtf8());

		// tokenvalue is less than 0
		fakeTokenValue = -1L;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				assetAccountDev, fakeTokenValue, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenValue must >= 0",
				response.getMessage().toStringUtf8());

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// tokenvalue is long.min
		fakeTokenValue = Long.MIN_VALUE;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "",
				maxFeeLimit, 0L, 0, 10000,
				assetAccountDev, fakeTokenValue, null, dev001Key,
				dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenValue must >= 0",
				response.getMessage().toStringUtf8());

		long tokenId = -1;
		long tokenValue = 0;
		long callValue = 10;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "", maxFeeLimit,
				callValue, 0, 10000, tokenId, tokenValue,
				null, dev001Key, dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenId must > 1000000",
				response.getMessage().toStringUtf8());

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		tokenId = Long.MIN_VALUE;
		tokenValue = 0;
		callValue = 10;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "", maxFeeLimit,
				callValue, 0, 10000, tokenId, tokenValue,
				null, dev001Key, dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenId must > 1000000",
				response.getMessage().toStringUtf8());

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		tokenId = 1000000;
		tokenValue = 0;
		callValue = 10;

		response = PublicMethed.deployContractAndGetResponse(contractName, abi, code, "", maxFeeLimit,
				callValue, 0, 10000, tokenId, tokenValue,
				null, dev001Key, dev001Address, blockingStubFull);

		Assert.assertFalse(response.getResult());
		Assert.assertEquals(CONTRACT_VALIDATE_ERROR, response.getCode());
		Assert.assertEquals("contract validate error : tokenId must > 1000000",
				response.getMessage().toStringUtf8());

		accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
		Long devAssetCountAfter = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountDev, blockingStubFull);
		Long userAssetCountAfter = PublicMethed.getAssetIssueValue(dev001Address, assetAccountUser,
				blockingStubFull);

		logger.info("after energyLimit is " + energyLimit);
		logger.info("after energyUsage is " + energyUsage);
		logger.info("after balanceAfter is " + balanceAfter);
		logger.info("after dev has AssetId: " + assetAccountDev + ", devAssetCountAfter: " + devAssetCountAfter);
		logger.info("after user has AssetId: " + assetAccountDev + ", userAssetCountAfter: " + userAssetCountAfter);

		Assert.assertEquals(devAssetCountBefore, devAssetCountAfter);
		Assert.assertEquals(userAssetCountBefore, userAssetCountAfter);

		PublicMethed.unFreezeBalance(fromAddress, testKey002, 1, dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(fromAddress, testKey002, 0, dev001Address, blockingStubFull);
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


