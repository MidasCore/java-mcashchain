package stest.tron.wallet.dailybuild.trctoken;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractTrcToken077 {


	private final String testNetAccountKey = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
	byte[] contractAddress = null;
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] grammarAddress = ecKey1.getAddress();
	String testKeyForGrammarAddress = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private Long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");
	private ManagedChannel channelSolidity = null;
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private ManagedChannel channelFull1 = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);

	@BeforeSuite
	public void beforeSuite() {
		Wallet wallet = new Wallet();
	}

	/**
	 * constructor.
	 */

	@BeforeClass(enabled = true)
	public void beforeClass() {
		PublicMethed.printAddress(testKeyForGrammarAddress);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		logger.info(Long.toString(PublicMethed.queryAccount(testNetAccountKey, blockingStubFull)
				.getBalance()));
	}

	@Test(enabled = false)
	public void testAddress001() {
		PublicMethed
				.sendcoin(grammarAddress, 100000000000L, testNetAccountAddress, testNetAccountKey,
						blockingStubFull);
		String contractName = "AddressTest";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken077_AddressTest");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken077_AddressTest");
		contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
				0L, 100, null, testKeyForGrammarAddress,
				grammarAddress, blockingStubFull);
		String txid = "";
		txid = PublicMethed.triggerContract(contractAddress,
				"addressTest()", "#", false,
				0, maxFeeLimit, grammarAddress, testKeyForGrammarAddress, blockingStubFull);
		Optional<TransactionInfo> infoById = null;
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		logger.info("infoById:" + infoById);


	}

	@Test(enabled = true, description = "The value of address is not at the beginning of 41")
	public void testAddress002() {
		PublicMethed
				.sendcoin(grammarAddress, 100000000000L, testNetAccountAddress, testNetAccountKey,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String contractName = "AddressTest";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken077_AddressTest1");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken077_AddressTest1");
		contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
				0L, 100, null, testKeyForGrammarAddress,
				grammarAddress, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String txid = "";
		txid = PublicMethed.triggerContract(contractAddress,
				"addressTest()", "#", false,
				0, maxFeeLimit, grammarAddress, testKeyForGrammarAddress, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = null;
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		logger.info("infoById:" + infoById);

		byte[] a = infoById.get().getContractResult(0).toByteArray();
		byte[] b = subByte(a, 11, 1);
		byte[] c = subByte(a, 0, 11);
		byte[] e = "41".getBytes();
		byte[] d = subByte(a, 12, 20);

		logger.info("a:" + ByteArray.toHexString(a));

		logger.info("b:" + ByteArray.toHexString(b));
		logger.info("c:" + ByteArray.toHexString(c));

		logger.info("d:" + ByteArray.toHexString(d));

		logger.info("41" + ByteArray.toHexString(d));
		String exceptedResult = "41" + ByteArray.toHexString(d);
		String realResult = ByteArray.toHexString(b);
		Assert.assertEquals(realResult, "00");
		Assert.assertNotEquals(realResult, "41");

		Assert.assertEquals(exceptedResult, ByteArray.toHexString(contractAddress));

	}


	/**
	 * constructor.
	 */

	public byte[] subByte(byte[] b, int off, int length) {
		byte[] b1 = new byte[length];
		System.arraycopy(b, off, b1, 0, length);
		return b1;

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
