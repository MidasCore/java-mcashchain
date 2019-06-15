package stest.tron.wallet.dailybuild.assetissue;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
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
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.concurrent.TimeUnit;

@Slf4j
public class WalletTestAssetIssue013 {

	private static final long now = System.currentTimeMillis();
	private static final long totalSupply = now;
	private static final long sendAmount = 10000000000L;
	private static final long netCostMeasure = 200L;
	private static String name = "AssetIssue013_" + Long.toString(now);
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	Long freeAssetNetLimit = 300L;
	Long publicFreeAssetNetLimit = 3000L;
	String description = "for case assetissue013";
	String url = "https://stest.assetissue013.url";
	//get account
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] asset013Address = ecKey1.getAddress();
	String testKeyForAssetIssue013 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] transferAssetAddress = ecKey2.getAddress();
	String transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);

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

	@Test(enabled = true, description = "Use transfer net when token owner has no enough net")
	public void testWhenNoEnoughFreeAssetNetLimitUseTransferNet() {

		//get account
		ECKey ecKey1 = new ECKey(Utils.getRandom());
		byte[] asset013Address = ecKey1.getAddress();
		final String testKeyForAssetIssue013 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

		ECKey ecKey2 = new ECKey(Utils.getRandom());
		final byte[] transferAssetAddress = ecKey2.getAddress();
		final String transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

		logger.info(testKeyForAssetIssue013);
		logger.info(transferAssetCreateKey);

		Assert.assertTrue(PublicMethed
				.sendcoin(asset013Address, sendAmount, fromAddress, testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Assert.assertTrue(PublicMethed
				.freezeBalance(asset013Address, 100000000L, 3, testKeyForAssetIssue013,
						blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Long start = System.currentTimeMillis() + 2000;
		Long end = System.currentTimeMillis() + 1000000000;
		Assert.assertTrue(PublicMethed
				.createAssetIssue(asset013Address, name, totalSupply, 1, 1, start, end, 1, description,
						url, freeAssetNetLimit, publicFreeAssetNetLimit, 1L, 1L, testKeyForAssetIssue013,
						blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account getAssetIdFromThisAccount;
		getAssetIdFromThisAccount = PublicMethed.queryAccount(asset013Address, blockingStubFull);
		long assetAccountId = getAssetIdFromThisAccount.getAssetIssuedId();

		//Transfer asset to an account.
		Assert.assertTrue(PublicMethed.transferAsset(
				transferAssetAddress, assetAccountId,
				10000000L, asset013Address, testKeyForAssetIssue013, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		//Transfer send some asset issue to default account, to test if this
		// transaction use the creator net.
		Assert.assertTrue(PublicMethed.transferAsset(toAddress, assetAccountId, 1L,
				transferAssetAddress, transferAssetCreateKey, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		//Before use transfer net, query the net used from creator and transfer.
		AccountResourceMessage assetCreatorNet = PublicMethed
				.getAccountResource(asset013Address, blockingStubFull);
		AccountResourceMessage assetTransferNet = PublicMethed
				.getAccountResource(transferAssetAddress, blockingStubFull);
		Long creatorBeforeNetUsed = assetCreatorNet.getBandwidthUsed();
		Long transferBeforeFreeNetUsed = assetTransferNet.getFreeBandwidthUsed();
		logger.info(Long.toString(creatorBeforeNetUsed));
		logger.info(Long.toString(transferBeforeFreeNetUsed));

		//Transfer send some asset issue to default account, to test if this
		// transaction use the transaction free net.
		Assert.assertTrue(PublicMethed.transferAsset(toAddress, assetAccountId, 1L,
				transferAssetAddress, transferAssetCreateKey, blockingStubFull));
		assetCreatorNet = PublicMethed
				.getAccountResource(asset013Address, blockingStubFull);
		assetTransferNet = PublicMethed
				.getAccountResource(transferAssetAddress, blockingStubFull);
		Long creatorAfterNetUsed = assetCreatorNet.getBandwidthUsed();
		Long transferAfterFreeNetUsed = assetTransferNet.getFreeBandwidthUsed();
		logger.info(Long.toString(creatorAfterNetUsed));
		logger.info(Long.toString(transferAfterFreeNetUsed));

		Assert.assertTrue(creatorAfterNetUsed - creatorBeforeNetUsed < netCostMeasure);
		Assert.assertTrue(transferAfterFreeNetUsed - transferBeforeFreeNetUsed > netCostMeasure);
	}

	/**
	 * constructor.
	 */

	@AfterClass(enabled = true)
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}


