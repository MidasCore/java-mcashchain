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
public class WalletTestAssetIssue007 {

	private static final long now = System.currentTimeMillis();
	private static final long totalSupply = now;
	private static final long sendAmount = 10000000000L;
	private static final long netCostMeasure = 200L;
	private static final Integer trxNum = 1;
	private static final Integer icoNum = 1;
	private static String name = "AssetIssue007_" + now;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	Long freeAssetNetLimit = 10000L;
	Long publicFreeAssetNetLimit = 10000L;
	String description = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetDescription");
	String url = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetUrl");
	//get account
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] asset007Address = ecKey1.getAddress();
	String testKeyForAssetIssue007 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] participateAssetAddress = ecKey2.getAddress();
	String participateAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
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
		PublicMethed.printAddress(testKeyForAssetIssue007);
		PublicMethed.printAddress(participateAssetCreateKey);

		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
	}

	@Test(enabled = true, description = "Participate asset issue use participate bandwidth")
	public void testParticipateAssetIssueUseParticipateBandwidth() {
		Assert.assertTrue(PublicMethed.sendcoin(asset007Address, sendAmount, fromAddress, testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Long start = System.currentTimeMillis() + 5000;
		Long end = System.currentTimeMillis() + 1000000000;
		Assert.assertTrue(PublicMethed
				.createAssetIssue(asset007Address, name, totalSupply, trxNum, icoNum, start, end, 1,
						description, url, freeAssetNetLimit, publicFreeAssetNetLimit, 1L, 1L,
						testKeyForAssetIssue007, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		logger.info(name);
		//Assert.assertTrue(PublicMethed.waitProduceNextBlock(blockingStubFull));
		//When no balance, participate an asset issue

		Account getAssetIdFromThisAccount;
		getAssetIdFromThisAccount = PublicMethed.queryAccount(asset007Address, blockingStubFull);
		long assetAccountId = getAssetIdFromThisAccount.getAssetIssuedId();
		logger.info(String.valueOf(assetAccountId));


		Assert.assertFalse(PublicMethed.participateAssetIssue(asset007Address, assetAccountId,
				1L, participateAssetAddress, participateAssetCreateKey, blockingStubFull));

		ByteString addressBs = ByteString.copyFrom(asset007Address);
		Account request = Account.newBuilder().setAddress(addressBs).build();
		AccountResourceMessage asset007NetMessage = blockingStubFull.getAccountResource(request);
		final Long asset007BeforeFreeNetUsed = asset007NetMessage.getFreeBandwidthUsed();

		//SendCoin to participate account.
		Assert.assertTrue(PublicMethed.sendcoin(participateAssetAddress, 10000000L,
				fromAddress, testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		addressBs = ByteString.copyFrom(participateAssetAddress);
		request = Account.newBuilder().setAddress(addressBs).build();
		AccountResourceMessage participateAccountNetMessage = blockingStubFull.getAccountResource(request);
		final Long participateAccountBeforeNetUsed = participateAccountNetMessage.getFreeBandwidthUsed();
		Assert.assertEquals(participateAccountBeforeNetUsed.longValue(), 0);

		//Participate an assetIssue, then query the net information.
		Assert.assertTrue(PublicMethed.participateAssetIssue(
				asset007Address, assetAccountId,
				1L, participateAssetAddress, participateAssetCreateKey, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		addressBs = ByteString.copyFrom(asset007Address);
		request = Account.newBuilder().setAddress(addressBs).build();
		asset007NetMessage = blockingStubFull.getAccountResource(request);
		final Long asset007AfterFreeNetUsed = asset007NetMessage.getFreeBandwidthUsed();

		addressBs = ByteString.copyFrom(participateAssetAddress);
		request = Account.newBuilder().setAddress(addressBs).build();
		participateAccountNetMessage = blockingStubFull.getAccountResource(request);
		final Long participateAccountAfterNetUsed = participateAccountNetMessage.getFreeBandwidthUsed();

		logger.info(Long.toString(asset007BeforeFreeNetUsed));
		logger.info(Long.toString(asset007AfterFreeNetUsed));
		logger.info(Long.toString(participateAccountBeforeNetUsed));
		logger.info(Long.toString(participateAccountAfterNetUsed));
		Assert.assertTrue(asset007AfterFreeNetUsed <= asset007BeforeFreeNetUsed);
		Assert.assertTrue(participateAccountAfterNetUsed - participateAccountBeforeNetUsed > 150);

		Assert.assertTrue(PublicMethed.participateAssetIssue(
				asset007Address, assetAccountId,
				1L, participateAssetAddress, participateAssetCreateKey, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Assert.assertTrue(PublicMethed.participateAssetIssue(
				asset007Address, assetAccountId,
				1L, participateAssetAddress, participateAssetCreateKey, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Account participateInfo = PublicMethed
				.queryAccount(participateAssetCreateKey, blockingStubFull);
		final Long beforeBalance = participateInfo.getBalance();
		Assert.assertTrue(PublicMethed.participateAssetIssue(
				asset007Address, assetAccountId,
				1L, participateAssetAddress, participateAssetCreateKey, blockingStubFull));
		participateInfo = PublicMethed.queryAccount(participateAssetCreateKey, blockingStubFull);
		final Long afterBalance = participateInfo.getBalance();

		Assert.assertTrue(beforeBalance - trxNum * 1 * icoNum >= afterBalance);
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


