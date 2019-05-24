package stest.tron.wallet.dailybuild.trctoken;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.api.WalletSolidityGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractTrcToken076 {


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

	@Test(enabled = true, description = "Origin test ")
	public void testDeployTransferTokenContract() {
		PublicMethed
				.sendcoin(grammarAddress, 100000000000L, testNetAccountAddress, testNetAccountKey,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String contractName = "originTest";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken076_originTest");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken076_originTest");
		contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
				0L, 100, null, testKeyForGrammarAddress,
				grammarAddress, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.triggerContract(contractAddress,
				"test()", "#", false,
				0, maxFeeLimit, grammarAddress, testKeyForGrammarAddress, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		String txid = "";
		txid = PublicMethed.triggerContract(contractAddress,
				"getResult1()", "#", false,
				0, maxFeeLimit, grammarAddress, testKeyForGrammarAddress, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = null;
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		logger.info("infoById:" + infoById);
		Long returnnumber = ByteArray.toLong(ByteArray.fromHexString(ByteArray.toHexString(
				infoById.get().getContractResult(0).toByteArray())));

		Assert.assertTrue(returnnumber == 1);

		txid = PublicMethed.triggerContract(contractAddress,
				"getResult2()", "#", false,
				0, maxFeeLimit, grammarAddress, testKeyForGrammarAddress, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		logger.info("-------------------------");

		logger.info("infoById:" + infoById);
		Long returnnumber2 = ByteArray.toLong(ByteArray.fromHexString(
				ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray())));

		Assert.assertTrue(returnnumber2 == 1);
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
