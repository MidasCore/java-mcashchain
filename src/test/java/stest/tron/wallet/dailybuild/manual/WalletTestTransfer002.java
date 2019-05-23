package stest.tron.wallet.dailybuild.manual;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.NumberMessage;
import io.midasprotocol.api.GrpcAPI.Return;
import io.midasprotocol.api.WalletExtensionGrpc;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.api.WalletSolidityGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.Block;
import io.midasprotocol.protos.Protocol.Transaction;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;
import stest.tron.wallet.common.client.utils.TransactionUtils;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WalletTestTransfer002 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);

	private ManagedChannel channelFull = null;
	private ManagedChannel channelSolidity = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
	private WalletExtensionGrpc.WalletExtensionBlockingStub blockingStubExtension = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);
	private String soliditynode = Configuration.getByPath("testng.conf")
			.getStringList("solidityNode.ip.list").get(0);

	public static String loadPubKey() {
		char[] buf = new char[0x100];
		return String.valueOf(buf, 32, 130);
	}

	@BeforeSuite
	public void beforeSuite() {
		Wallet wallet = new Wallet();
	}

	/**
	 * constructor.
	 */

	@BeforeClass
	public void beforeClass() {
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

		channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
				.usePlaintext(true)
				.build();
		blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
		blockingStubExtension = WalletExtensionGrpc.newBlockingStub(channelSolidity);
	}

	@Test(enabled = false)
	public void testGetTotalTransaction() {
		NumberMessage beforeGetTotalTransaction = blockingStubFull
				.totalTransaction(GrpcAPI.EmptyMessage.newBuilder().build());
		logger.info(Long.toString(beforeGetTotalTransaction.getNum()));
		Long beforeTotalTransaction = beforeGetTotalTransaction.getNum();
		Assert.assertTrue(PublicMethed.sendcoin(toAddress, 1000000, fromAddress,
				testKey002, blockingStubFull));
		NumberMessage afterGetTotalTransaction = blockingStubFull
				.totalTransaction(GrpcAPI.EmptyMessage.newBuilder().build());
		logger.info(Long.toString(afterGetTotalTransaction.getNum()));
		Long afterTotalTransaction = afterGetTotalTransaction.getNum();
		Assert.assertTrue(afterTotalTransaction - beforeTotalTransaction > 0);

		//Improve coverage.
		afterGetTotalTransaction.equals(beforeGetTotalTransaction);
		afterGetTotalTransaction.equals(afterGetTotalTransaction);
		afterGetTotalTransaction.hashCode();
		afterGetTotalTransaction.isInitialized();
		afterGetTotalTransaction.getSerializedSize();
		afterGetTotalTransaction.getDefaultInstanceForType();
		afterGetTotalTransaction.getParserForType();
		afterGetTotalTransaction.getUnknownFields();


	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (channelSolidity != null) {
			channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	/**
	 * constructor.
	 */

	public Boolean sendcoin(byte[] to, long amount, byte[] owner, String priKey) {

		//String priKey = testKey002;
		ECKey temKey = null;
		try {
			BigInteger priK = new BigInteger(priKey, 16);
			temKey = ECKey.fromPrivate(priK);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		ECKey ecKey = temKey;
		Account search = queryAccount(ecKey, blockingStubFull);

		Contract.TransferContract.Builder builder = Contract.TransferContract.newBuilder();
		ByteString bsTo = ByteString.copyFrom(to);
		ByteString bsOwner = ByteString.copyFrom(owner);
		builder.setToAddress(bsTo);
		builder.setOwnerAddress(bsOwner);
		builder.setAmount(amount);

		Contract.TransferContract contract = builder.build();
		Transaction transaction = blockingStubFull.createTransaction(contract);
		if (transaction == null || transaction.getRawData().getContractCount() == 0) {
			return false;
		}
		transaction = signTransaction(ecKey, transaction);
		Return response = blockingStubFull.broadcastTransaction(transaction);
		if (response.getResult() == false) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * constructor.
	 */

	public Account queryAccount(ECKey ecKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
		byte[] address;
		if (ecKey == null) {
			String pubKey = loadPubKey(); //04 PubKey[128]
			if (StringUtils.isEmpty(pubKey)) {
				logger.warn("Warning: QueryAccount failed, no wallet address !!");
				return null;
			}
			byte[] pubKeyAsc = pubKey.getBytes();
			byte[] pubKeyHex = Hex.decode(pubKeyAsc);
			ecKey = ECKey.fromPublicOnly(pubKeyHex);
		}
		return grpcQueryAccount(ecKey.getAddress(), blockingStubFull);
	}

	public byte[] getAddress(ECKey ecKey) {
		return ecKey.getAddress();
	}

	/**
	 * constructor.
	 */

	public Account grpcQueryAccount(byte[] address, WalletGrpc.WalletBlockingStub blockingStubFull) {
		ByteString addressBs = ByteString.copyFrom(address);
		Account request = Account.newBuilder().setAddress(addressBs).build();
		return blockingStubFull.getAccount(request);
	}

	/**
	 * constructor.
	 */

	public Block getBlock(long blockNum, WalletGrpc.WalletBlockingStub blockingStubFull) {
		NumberMessage.Builder builder = NumberMessage.newBuilder();
		builder.setNum(blockNum);
		return blockingStubFull.getBlockByNum(builder.build());

	}

	private Transaction signTransaction(ECKey ecKey, Transaction transaction) {
		if (ecKey == null || ecKey.getPrivKey() == null) {
			logger.warn("Warning: Can't sign,there is no private key !!");
			return null;
		}
		transaction = TransactionUtils.setTimestamp(transaction);
		return TransactionUtils.sign(transaction, ecKey);
	}
}


