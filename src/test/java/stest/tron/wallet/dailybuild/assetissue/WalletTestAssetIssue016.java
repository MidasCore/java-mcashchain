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
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.AccountResourceMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.Account;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.concurrent.TimeUnit;

@Slf4j
public class WalletTestAssetIssue016 {

	private static final long now = System.currentTimeMillis();
	private static final long totalSupply = now;
	private static final long sendAmount = 10000000000L;
	private static final long netCostMeasure = 200L;
	private static String name = "AssetIssue016_" + Long.toString(now);
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	Long freeAssetNetLimit = 30000L;
	Long publicFreeAssetNetLimit = 30000L;
	String description = "for case assetissue016";
	String url = "https://stest.assetissue016.url";
	long assetAccountId;
	//get account
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] asset016Address = ecKey1.getAddress();
	String testKeyForAssetIssue016 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] transferAssetAddress = ecKey2.getAddress();
	String transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(1);

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

	@Test(enabled = true, description = "Get asset issue net resource")
	public void testGetAssetIssueNet() {
		//get account
		ecKey1 = new ECKey(Utils.getRandom());
		asset016Address = ecKey1.getAddress();
		testKeyForAssetIssue016 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

		ecKey2 = new ECKey(Utils.getRandom());
		transferAssetAddress = ecKey2.getAddress();
		transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

		PublicMethed.printAddress(testKeyForAssetIssue016);
		PublicMethed.printAddress(transferAssetCreateKey);

		Assert.assertTrue(PublicMethed
				.sendcoin(asset016Address, sendAmount, fromAddress, testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Long start = System.currentTimeMillis() + 2000;
		Long end = System.currentTimeMillis() + 1000000000;
		Assert.assertTrue(PublicMethed
				.createAssetIssue(asset016Address, name, totalSupply, 1, 1, start, end, 1, description,
						url, freeAssetNetLimit, publicFreeAssetNetLimit, 1L, 1L, testKeyForAssetIssue016,
						blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account getAssetIdFromThisAccount;
		getAssetIdFromThisAccount = PublicMethed.queryAccount(asset016Address, blockingStubFull);
		assetAccountId = getAssetIdFromThisAccount.getAssetIssuedId();

		AccountResourceMessage assetIssueInfo = PublicMethed
				.getAccountResource(asset016Address, blockingStubFull);
		Assert.assertTrue(assetIssueInfo.getAssetBandwidthLimitCount() == 1);
		Assert.assertTrue(assetIssueInfo.getAssetBandwidthUsedCount() == 1);
		Assert.assertFalse(assetIssueInfo.getAssetBandwidthLimitMap().isEmpty());
		Assert.assertFalse(assetIssueInfo.getAssetBandwidthUsedMap().isEmpty());

		GrpcAPI.BytesMessage request = GrpcAPI.BytesMessage.newBuilder()
				.setValue(ByteString.copyFrom(ByteArray.fromLong(assetAccountId))).build();
		Contract.AssetIssueContract assetIssueByName = blockingStubFull.getAssetIssueByName(request);
		Assert.assertTrue(assetIssueByName.getFreeAssetBandwidthLimit() == freeAssetNetLimit);
		Assert.assertTrue(assetIssueByName.getPublicFreeAssetBandwidthLimit() == publicFreeAssetNetLimit);
		Assert.assertTrue(assetIssueByName.getPublicLatestFreeBandwidthTime() == 0);
		assetIssueInfo.hashCode();
		assetIssueInfo.getSerializedSize();
		assetIssueInfo.equals(assetIssueInfo);

		PublicMethed.transferAsset(transferAssetAddress, assetAccountId, 1000L,
				asset016Address, testKeyForAssetIssue016, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.transferAsset(toAddress, assetAccountId, 100L,
				transferAssetAddress, transferAssetCreateKey, blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		assetIssueByName = blockingStubFull.getAssetIssueByName(request);
		Assert.assertTrue(assetIssueByName.getPublicLatestFreeBandwidthTime() == 0);
		Assert.assertTrue(assetIssueByName.getPublicFreeAssetBandwidthUsage() == 0);

		Assert.assertTrue(PublicMethed.freezeBalance(asset016Address, 30000000L,
				3, testKeyForAssetIssue016, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.transferAsset(toAddress, assetAccountId, 100L,
				transferAssetAddress, transferAssetCreateKey, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		assetIssueByName = blockingStubFull.getAssetIssueByName(request);
		Assert.assertTrue(assetIssueByName.getPublicLatestFreeBandwidthTime() > 0);
		Assert.assertTrue(assetIssueByName.getPublicFreeAssetBandwidthUsage() > 150);


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


