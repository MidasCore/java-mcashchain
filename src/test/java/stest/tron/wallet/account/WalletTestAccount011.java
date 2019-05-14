package stest.tron.wallet.account;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.EmptyMessage;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.concurrent.TimeUnit;

@Slf4j
public class WalletTestAccount011 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] account011Address = ecKey1.getAddress();
	String account011Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private ManagedChannel channelSolidity = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);
	private String soliditynode = Configuration.getByPath("testng.conf")
			.getStringList("solidityNode.ip.list").get(0);

	@BeforeSuite
	public void beforeSuite() {
		Wallet wallet = new Wallet();
	}

	/**
	 * constructor.
	 */

	@BeforeClass(enabled = true)
	public void beforeClass() {
		PublicMethed.printAddress(account011Key);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

		channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
				.usePlaintext(true)
				.build();
		blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);

	}

	@Test(enabled = true)
	public void testgenerateAddress() {
		EmptyMessage.Builder builder = EmptyMessage.newBuilder();
		blockingStubFull.generateAddress(builder.build());
		blockingStubSolidity.generateAddress(builder.build());
	}

	/**
	 * constructor.
	 */

	@AfterClass(enabled = true)
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (channelSolidity != null) {
			channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);

		}

	}
}
