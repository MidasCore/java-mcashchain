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
import io.midasprotocol.api.GrpcAPI.WitnessList;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.Block;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;
import stest.tron.wallet.common.client.utils.TransactionUtils;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

//import stest.tron.wallet.common.client.AccountComparator;

@Slf4j
public class WalletTestWitness003 {

	private static final byte[] INVAILD_ADDRESS = Base58
			.decodeFromBase58Check("27cu1ozb4mX3m2afY68FSAqn3HmMp815d48");
	private static final Long costForCreateWitness = 9999000000L;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	private final String testUpdateWitnessKey = Configuration.getByPath("testng.conf")
			.getString("witness.key1");
	private final byte[] updateAddress = PublicMethed.getFinalAddress(testUpdateWitnessKey);
	String createWitnessUrl = "http://www.createwitnessurl.com";
	String updateWitnessUrl = "http://www.updatewitnessurl.com";
	String nullUrl = "";
	String spaceUrl = "          ##################~!@#$%^&*()_+}{|:'/.,<>?|]=-";
	byte[] createUrl = createWitnessUrl.getBytes();
	byte[] updateUrl = updateWitnessUrl.getBytes();
	byte[] wrongUrl = nullUrl.getBytes();
	byte[] updateSpaceUrl = spaceUrl.getBytes();
	//get account
	ECKey ecKey = new ECKey(Utils.getRandom());
	byte[] lowBalAddress = ecKey.getAddress();
	String lowBalTest = ByteArray.toHexString(ecKey.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);

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
		logger.info(lowBalTest);
		logger.info(ByteArray.toHexString(PublicMethed.getFinalAddress(lowBalTest)));
		logger.info(Base58.encodeBase58(PublicMethed.getFinalAddress(lowBalTest)));

		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
	}

	@Test(enabled = true, description = "Invaild account to apply create witness")
	public void testInvaildToApplyBecomeWitness() {
		Assert.assertFalse(createWitness(INVAILD_ADDRESS, createUrl, testKey002));
	}

	@Test(enabled = true, description = "Create witness")
	public void testCreateWitness() {
		//If you are already is witness, apply failed
		//createWitness(fromAddress, createUrl, testKey002);
		//Assert.assertFalse(createWitness(fromAddress, createUrl, testKey002));

		//No balance,try to create witness.
		Assert.assertFalse(createWitness(lowBalAddress, createUrl, lowBalTest));

		//Send enough coin to the apply account to make that account
		// has ability to apply become witness.
		GrpcAPI.WitnessList witnesslist = blockingStubFull
				.listWitnesses(GrpcAPI.EmptyMessage.newBuilder().build());
		Optional<WitnessList> result = Optional.ofNullable(witnesslist);
		GrpcAPI.WitnessList witnessList = result.get();
		if (result.get().getWitnessesCount() < 6) {
			Assert.assertTrue(PublicMethed
					.sendcoin(lowBalAddress, costForCreateWitness, fromAddress, testKey002,
							blockingStubFull));
			Assert.assertTrue(createWitnessNotBroadcast(lowBalAddress, createUrl, lowBalTest));

		}
	}

	@Test(enabled = true, description = "Update witness")
	public void testUpdateWitness() {
		GrpcAPI.WitnessList witnesslist = blockingStubFull
				.listWitnesses(GrpcAPI.EmptyMessage.newBuilder().build());
		Optional<WitnessList> result = Optional.ofNullable(witnesslist);
		GrpcAPI.WitnessList witnessList = result.get();
		if (result.get().getWitnessesCount() < 6) {
			//null url, update failed
			Assert.assertFalse(updateWitness(updateAddress, wrongUrl, testUpdateWitnessKey));
			//Content space and special char, update success
			Assert.assertTrue(updateWitness(updateAddress, updateSpaceUrl, testUpdateWitnessKey));
			//update success
			Assert.assertTrue(updateWitness(updateAddress, updateUrl, testUpdateWitnessKey));
		} else {
			logger.info("Update witness case had been test.This time skip it.");
		}


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

	/**
	 * constructor.
	 */

	public Boolean createWitness(byte[] owner, byte[] url, String priKey) {
		ECKey temKey = null;
		try {
			BigInteger priK = new BigInteger(priKey, 16);
			temKey = ECKey.fromPrivate(priK);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		final ECKey ecKey = temKey;

		Contract.WitnessCreateContract.Builder builder = Contract.WitnessCreateContract.newBuilder();
		builder.setOwnerAddress(ByteString.copyFrom(owner));
		builder.setUrl(ByteString.copyFrom(url));
		Contract.WitnessCreateContract contract = builder.build();
		Protocol.Transaction transaction = blockingStubFull.createWitness(contract);
		if (transaction == null || transaction.getRawData().getContractCount() == 0) {
			return false;
		}
		transaction = signTransaction(ecKey, transaction);
		GrpcAPI.Return response = PublicMethed.broadcastTransaction(transaction, blockingStubFull);
		return response.getResult();

	}

	/**
	 * constructor.
	 */

	public Boolean createWitnessNotBroadcast(byte[] owner, byte[] url, String priKey) {
		ECKey temKey = null;
		try {
			BigInteger priK = new BigInteger(priKey, 16);
			temKey = ECKey.fromPrivate(priK);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		final ECKey ecKey = temKey;

		Contract.WitnessCreateContract.Builder builder = Contract.WitnessCreateContract.newBuilder();
		builder.setOwnerAddress(ByteString.copyFrom(owner));
		builder.setUrl(ByteString.copyFrom(url));
		Contract.WitnessCreateContract contract = builder.build();
		Protocol.Transaction transaction = blockingStubFull.createWitness(contract);
		if (transaction == null || transaction.getRawData().getContractCount() == 0) {
			return false;
		}
		transaction = signTransaction(ecKey, transaction);
		return true;
	}

	/**
	 * constructor.
	 */

	public Boolean updateWitness(byte[] owner, byte[] url, String priKey) {
		ECKey temKey = null;
		try {
			BigInteger priK = new BigInteger(priKey, 16);
			temKey = ECKey.fromPrivate(priK);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		final ECKey ecKey = temKey;

		Contract.WitnessUpdateContract.Builder builder = Contract.WitnessUpdateContract.newBuilder();
		builder.setOwnerAddress(ByteString.copyFrom(owner));
		builder.setUpdateUrl(ByteString.copyFrom(url));
		Contract.WitnessUpdateContract contract = builder.build();
		Protocol.Transaction transaction = blockingStubFull.updateWitness(contract);
		if (transaction == null || transaction.getRawData().getContractCount() == 0) {
			logger.info("transaction == null");
			return false;
		}
		transaction = signTransaction(ecKey, transaction);
		GrpcAPI.Return response = PublicMethed.broadcastTransaction(transaction, blockingStubFull);
		if (response.getResult() == false) {
			logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
			logger.info("response.getRestult() == false");
			return false;
		} else {
			return true;
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
		final ECKey ecKey = temKey;

		Contract.TransferContract.Builder builder = Contract.TransferContract.newBuilder();
		ByteString bsTo = ByteString.copyFrom(to);
		ByteString bsOwner = ByteString.copyFrom(owner);
		builder.setToAddress(bsTo);
		builder.setOwnerAddress(bsOwner);
		builder.setAmount(amount);

		Contract.TransferContract contract = builder.build();
		Protocol.Transaction transaction = blockingStubFull.createTransaction(contract);
		if (transaction == null || transaction.getRawData().getContractCount() == 0) {
			return false;
		}
		transaction = signTransaction(ecKey, transaction);
		GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
		if (response.getResult() == false) {
			logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
			return false;
		} else {
			return true;
		}
	}

	/**
	 * constructor.
	 */

	public Account queryAccount(String priKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
		byte[] address;
		ECKey temKey = null;
		try {
			BigInteger priK = new BigInteger(priKey, 16);
			temKey = ECKey.fromPrivate(priK);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		ECKey ecKey = temKey;
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

	private Protocol.Transaction signTransaction(ECKey ecKey, Protocol.Transaction transaction) {
		if (ecKey == null || ecKey.getPrivKey() == null) {
			logger.warn("Warning: Can't sign,there is no private key !!");
			return null;
		}
		transaction = TransactionUtils.setTimestamp(transaction);
		return TransactionUtils.sign(transaction, ecKey);
	}
}


