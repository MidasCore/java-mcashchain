package stest.tron.wallet.transfer;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI.BytesMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.api.WalletSolidityGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WalletTestTransfer007 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);


	private ManagedChannel channelFull = null;
	private ManagedChannel searchChannelFull = null;

	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidityInFullnode = null;

	private WalletGrpc.WalletBlockingStub searchBlockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);
	private String searchFullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);
	private ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] sendAccountAddress = ecKey1.getAddress();
	String sendAccountKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private ManagedChannel channelSolidity = null;
	private ManagedChannel channelSolidityInFullnode = null;
	private String soliditynode = Configuration.getByPath("testng.conf")
			.getStringList("solidityNode.ip.list").get(0);
  /*  private String solidityInFullnode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(1);*/


	/**
	 * constructor.
	 */

	@BeforeClass
	public void beforeClass() {
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

		searchChannelFull = ManagedChannelBuilder.forTarget(searchFullnode)
				.usePlaintext(true)
				.build();
		searchBlockingStubFull = WalletGrpc.newBlockingStub(searchChannelFull);

		channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
				.usePlaintext(true)
				.build();
		blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);

    /*    channelSolidityInFullnode = ManagedChannelBuilder.forTarget(solidityInFullnode)
        .usePlaintext(true)
        .build();
    blockingStubSolidityInFullnode = WalletSolidityGrpc.newBlockingStub(channelSolidityInFullnode);
    */
	}


	@Test
	public void testSendCoin() {
		String transactionId = PublicMethed.sendcoinGetTransactionId(sendAccountAddress, 90000000000L,
				fromAddress, testKey002, blockingStubFull);
		Optional<Transaction> infoById = PublicMethed
				.getTransactionById(transactionId, blockingStubFull);
		Long timestamptis = PublicMethed.printTransactionRow(infoById.get().getRawData());
		Long timestamptispBlockOne = PublicMethed.getBlock(1, blockingStubFull).getBlockHeader()
				.getRawData().getTimestamp();
		Assert.assertTrue(timestamptis >= timestamptispBlockOne);
	}

	@Test
	public void testSendCoin2() {
		String transactionId = PublicMethed.sendcoinGetTransactionId(sendAccountAddress, 90000000000L,
				fromAddress, testKey002, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<Transaction> infoById = PublicMethed
				.getTransactionById(transactionId, blockingStubFull);
		Long timestamptis = PublicMethed.printTransactionRow(infoById.get().getRawData());
		Long timestampBlockOne = PublicMethed.getBlock(1, blockingStubFull).getBlockHeader()
				.getRawData().getTimestamp();
		Assert.assertTrue(timestamptis >= timestampBlockOne);
		PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);

		infoById = PublicMethed.getTransactionById(transactionId, blockingStubSolidity);
		timestamptis = PublicMethed.printTransactionRow(infoById.get().getRawData());
		timestampBlockOne = PublicMethed.getBlock(1, blockingStubFull).getBlockHeader()
				.getRawData().getTimestamp();
		Assert.assertTrue(timestamptis >= timestampBlockOne);

		ByteString bsTxid = ByteString.copyFrom(ByteArray.fromHexString(transactionId));
		BytesMessage request = BytesMessage.newBuilder().setValue(bsTxid).build();
		TransactionInfo transactionInfo;

		transactionInfo = blockingStubSolidity.getTransactionInfoById(request);
		Assert.assertTrue(transactionInfo.getBlockTimeStamp() >= timestampBlockOne);

		transactionInfo = blockingStubFull.getTransactionInfoById(request);
		Assert.assertTrue(transactionInfo.getBlockTimeStamp() >= timestampBlockOne);

		//transactionInfo = blockingStubSolidityInFullnode.getTransactionInfoById(request);
		//Assert.assertTrue(transactionInfo.getBlockTimeStamp() >= timestampBlockOne);

	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (searchChannelFull != null) {
			searchChannelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}


}
