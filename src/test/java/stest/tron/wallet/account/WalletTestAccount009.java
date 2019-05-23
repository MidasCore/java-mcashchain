package stest.tron.wallet.account;

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
public class WalletTestAccount009 {

	private static final long now = System.currentTimeMillis();
	private static final long totalSupply = now;
	private static final long sendAmount = 10000000000L;
	private static final long FREENETLIMIT = 5000L;
	private static final long BASELINE = 4800L;
	private static String name = "AssetIssue012_" + Long.toString(now);
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] account009Address = ecKey1.getAddress();
	String account009Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] account009SecondAddress = ecKey2.getAddress();
	String account009SecondKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	ECKey ecKey3 = new ECKey(Utils.getRandom());
	byte[] account009InvalidAddress = ecKey3.getAddress();
	String account009InvalidKey = ByteArray.toHexString(ecKey3.getPrivKeyBytes());
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
		PublicMethed.printAddress(account009Key);
		PublicMethed.printAddress(account009SecondKey);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

	}

	@Test(enabled = true)
	public void testGetEnergy() {
		Assert.assertTrue(PublicMethed.sendcoin(account009Address, 10000000,
				fromAddress, testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.sendcoin(account009SecondAddress, 10000000,
				fromAddress, testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.sendcoin(account009InvalidAddress, 10000000,
				fromAddress, testKey002, blockingStubFull));

		Account account009Info = PublicMethed.queryAccount(account009Key, blockingStubFull);
		logger.info(Long.toString(
				account009Info.getAccountResource().getFrozenBalanceForEnergy().getExpireTime()));
		Assert.assertTrue(account009Info.getAccountResource().getEnergyUsage() == 0);
		Assert.assertTrue(account009Info.getAccountResource().getFrozenBalanceForEnergy()
				.getExpireTime() == 0);

		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(account009Address, 1000000L,
				3, 1, account009Key, blockingStubFull));
		account009Info = PublicMethed.queryAccount(account009Key, blockingStubFull);
		Assert.assertTrue(account009Info.getAccountResource().getEnergyUsage() == 0);
		Assert.assertTrue(account009Info.getAccountResource().getFrozenBalanceForEnergy()
				.getFrozenBalance() == 1000000L);

		AccountResourceMessage account009Resource = PublicMethed.getAccountResource(account009Address,
				blockingStubFull);
		Assert.assertTrue(account009Resource.getTotalEnergyLimit() >= 50000000000L);
		Assert.assertTrue(account009Resource.getEnergyLimit() > 0);
		Assert.assertTrue(account009Resource.getTotalEnergyWeight() >= 1);
	}

	@Test(enabled = true)
	public void testGetEnergyInvalid() {
		//The resourceCode can only be 0 or 1
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(account009InvalidAddress,
				1000000L, 3, 0, account009InvalidKey, blockingStubFull));
		Assert.assertFalse(PublicMethed.freezeBalanceGetEnergy(account009InvalidAddress, 1000000L,
				3, -1, account009InvalidKey, blockingStubFull));
		Assert.assertFalse(PublicMethed.freezeBalanceGetEnergy(account009InvalidAddress, 1000000L,
				3, 2, account009InvalidKey, blockingStubFull));

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


