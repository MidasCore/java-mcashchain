package stest.tron.wallet.dailybuild.manual;

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
public class WalletTestAccount010 {

	private static final long now = System.currentTimeMillis();
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] account010Address = ecKey1.getAddress();
	String account010Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] account010SecondAddress = ecKey2.getAddress();
	String account010SecondKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	ECKey ecKey3 = new ECKey(Utils.getRandom());
	byte[] account010InvalidAddress = ecKey3.getAddress();
	String account010InvalidKey = ByteArray.toHexString(ecKey3.getPrivKeyBytes());
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

	@BeforeClass(enabled = false)
	public void beforeClass() {
		PublicMethed.printAddress(account010Key);
		PublicMethed.printAddress(account010SecondKey);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);


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


