package stest.tron.wallet.exchangeandtoken;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI.ExchangeList;
import io.midasprotocol.api.GrpcAPI.PaginatedMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.api.WalletSolidityGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.Exchange;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WalletExchange001 {

	private static final long now = System.currentTimeMillis();
	private static final long totalSupply = 1000000001L;
	private static String name1 = "exchange001_1_" + Long.toString(now);
	private static String name2 = "exchange001_2_" + Long.toString(now);
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
	String description = "just-test";
	String url = "https://github.com/tronprotocol/wallet-cli/";
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] exchange001Address = ecKey1.getAddress();
	String exchange001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] secondExchange001Address = ecKey2.getAddress();
	String secondExchange001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	Long secondTransferAssetToFirstAccountNum = 100000000L;
	Account firstAccount;
	long assetAccountId1;
	long assetAccountId2;
	Optional<ExchangeList> listExchange;
	Optional<Exchange> exchangeIdInfo;
	Integer exchangeId = 0;
	Integer exchangeRate = 10;
	Long firstTokenInitialBalance = 10000L;
	Long secondTokenInitialBalance = firstTokenInitialBalance * exchangeRate;
	private ManagedChannel channelFull = null;
	private ManagedChannel channelSolidity = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
	private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
			.get(0);
	private String soliditynode = Configuration.getByPath("testng.conf")
			.getStringList("solidityNode.ip.list").get(1);

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

		channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
				.usePlaintext(true)
				.build();
		blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
	}

	@Test(enabled = true)
	public void test1CreateUsedAsset() {
		ecKey1 = new ECKey(Utils.getRandom());
		exchange001Address = ecKey1.getAddress();
		exchange001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

		ecKey2 = new ECKey(Utils.getRandom());
		secondExchange001Address = ecKey2.getAddress();
		secondExchange001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

		PublicMethed.printAddress(exchange001Key);
		PublicMethed.printAddress(secondExchange001Key);

		Assert.assertTrue(PublicMethed.sendcoin(exchange001Address, 10240000000L, fromAddress,
				testKey002, blockingStubFull));
		Assert.assertTrue(PublicMethed.sendcoin(secondExchange001Address, 10240000000L, toAddress,
				testKey003, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Long start = System.currentTimeMillis() + 5000L;
		Long end = System.currentTimeMillis() + 5000000L;
		Assert.assertTrue(PublicMethed.createAssetIssue(exchange001Address, name1, totalSupply, 1,
				1, start, end, 1, description, url, 10000L, 10000L,
				1L, 1L, exchange001Key, blockingStubFull));
		Assert.assertTrue(PublicMethed.createAssetIssue(secondExchange001Address, name2, totalSupply, 1,
				1, start, end, 1, description, url, 10000L, 10000L,
				1L, 1L, secondExchange001Key, blockingStubFull));
	}

	@Test(enabled = true)
	public void test2CreateExchange() {
		listExchange = PublicMethed.getExchangeList(blockingStubFull);
		final Integer beforeCreateExchangeNum = listExchange.get().getExchangesCount();
		exchangeId = listExchange.get().getExchangesCount();

		Account getAssetIdFromThisAccount;
		getAssetIdFromThisAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		assetAccountId1 = getAssetIdFromThisAccount.getAssetIssuedId();

		getAssetIdFromThisAccount = PublicMethed
				.queryAccount(secondExchange001Address, blockingStubFull);
		assetAccountId2 = getAssetIdFromThisAccount.getAssetIssuedId();

		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Assert.assertTrue(PublicMethed.transferAsset(exchange001Address, assetAccountId2,
				secondTransferAssetToFirstAccountNum, secondExchange001Address,
				secondExchange001Key, blockingStubFull));
		//CreateExchange
		Assert.assertTrue(
				PublicMethed.exchangeCreate(assetAccountId1, firstTokenInitialBalance,
						assetAccountId2, secondTokenInitialBalance, exchange001Address,
						exchange001Key,
						blockingStubFull));
		listExchange = PublicMethed.getExchangeList(blockingStubFull);
		Integer afterCreateExchangeNum = listExchange.get().getExchangesCount();
		Assert.assertTrue(afterCreateExchangeNum - beforeCreateExchangeNum == 1);
		exchangeId = listExchange.get().getExchangesCount();

	}

	@Test(enabled = true)
	public void test3ListExchange() {
		listExchange = PublicMethed.getExchangeList(blockingStubFull);
		for (Integer i = 0; i < listExchange.get().getExchangesCount(); i++) {
			Assert.assertFalse(ByteArray.toHexString(listExchange.get().getExchanges(i)
					.getCreatorAddress().toByteArray()).isEmpty());
			Assert.assertTrue(listExchange.get().getExchanges(i).getExchangeId() > 0);
			Assert.assertFalse(listExchange.get().getExchanges(i).getFirstTokenId() == 0);
			Assert.assertTrue(listExchange.get().getExchanges(i).getFirstTokenBalance() > 0);
		}
	}

	@Test(enabled = true)
	public void test4InjectExchange() {
		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubFull);
		final Long beforeExchangeToken1Balance = exchangeIdInfo.get().getFirstTokenBalance();
		final Long beforeExchangeToken2Balance = exchangeIdInfo.get().getSecondTokenBalance();

		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Long beforeToken1Balance = 0L;
		Long beforeToken2Balance = 0L;
		for (long id : firstAccount.getAssetsMap().keySet()) {
			if (assetAccountId1 == id) {
				beforeToken1Balance = firstAccount.getAssetsMap().get(id);
			}
			if (assetAccountId2 == id) {
				beforeToken2Balance = firstAccount.getAssetsMap().get(id);
			}
		}
		logger.info("before token 1 balance is " + beforeToken1Balance);
		logger.info("before token 2 balance is " + beforeToken2Balance);
		Integer injectBalance = 100;
		Assert.assertTrue(
				PublicMethed.injectExchange(exchangeId, assetAccountId1, injectBalance,
						exchange001Address, exchange001Key, blockingStubFull));
		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Long afterToken1Balance = 0L;
		Long afterToken2Balance = 0L;
		for (long id : firstAccount.getAssetsMap().keySet()) {
			if (assetAccountId1 == id) {
				afterToken1Balance = firstAccount.getAssetsMap().get(id);
			}
			if (assetAccountId2 == id) {
				afterToken2Balance = firstAccount.getAssetsMap().get(id);
			}
		}
		logger.info("before token 1 balance is " + afterToken1Balance);
		logger.info("before token 2 balance is " + afterToken2Balance);

		Assert.assertTrue(beforeToken1Balance - afterToken1Balance == injectBalance);
		Assert.assertTrue(beforeToken2Balance - afterToken2Balance == injectBalance
				* exchangeRate);

		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubFull);
		Long afterExchangeToken1Balance = exchangeIdInfo.get().getFirstTokenBalance();
		Long afterExchangeToken2Balance = exchangeIdInfo.get().getSecondTokenBalance();
		Assert.assertTrue(afterExchangeToken1Balance - beforeExchangeToken1Balance
				== injectBalance);
		Assert.assertTrue(afterExchangeToken2Balance - beforeExchangeToken2Balance
				== injectBalance * exchangeRate);
	}

	@Test(enabled = true)
	public void test5WithdrawExchange() {
		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubFull);
		final Long beforeExchangeToken1Balance = exchangeIdInfo.get().getFirstTokenBalance();
		final Long beforeExchangeToken2Balance = exchangeIdInfo.get().getSecondTokenBalance();

		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Long beforeToken1Balance = 0L;
		Long beforeToken2Balance = 0L;
		for (long id : firstAccount.getAssetsMap().keySet()) {
			if (assetAccountId1 == id) {
				beforeToken1Balance = firstAccount.getAssetsMap().get(id);
			}
			if (assetAccountId2 == id) {
				beforeToken2Balance = firstAccount.getAssetsMap().get(id);
			}
		}

		logger.info("before token 1 balance is " + beforeToken1Balance);
		logger.info("before token 2 balance is " + beforeToken2Balance);
		Integer withdrawNum = 200;
		Assert.assertTrue(
				PublicMethed.exchangeWithdraw(exchangeId, assetAccountId1, withdrawNum,
						exchange001Address, exchange001Key, blockingStubFull));
		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Long afterToken1Balance = 0L;
		Long afterToken2Balance = 0L;
		for (long id : firstAccount.getAssetsMap().keySet()) {
			if (assetAccountId1 == id) {
				afterToken1Balance = firstAccount.getAssetsMap().get(id);
			}
			if (assetAccountId2 == id) {
				afterToken2Balance = firstAccount.getAssetsMap().get(id);
			}
		}

		logger.info("before token 1 balance is " + afterToken1Balance);
		logger.info("before token 2 balance is " + afterToken2Balance);

		Assert.assertTrue(afterToken1Balance - beforeToken1Balance == withdrawNum);
		Assert.assertTrue(afterToken2Balance - beforeToken2Balance == withdrawNum
				* exchangeRate);
		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubFull);
		Long afterExchangeToken1Balance = exchangeIdInfo.get().getFirstTokenBalance();
		Long afterExchangeToken2Balance = exchangeIdInfo.get().getSecondTokenBalance();
		Assert.assertTrue(afterExchangeToken1Balance - beforeExchangeToken1Balance
				== -withdrawNum);
		Assert.assertTrue(afterExchangeToken2Balance - beforeExchangeToken2Balance
				== -withdrawNum * exchangeRate);


	}

	@Test(enabled = true)
	public void test6TransactionExchange() {
		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubFull);
		final Long beforeExchangeToken1Balance = exchangeIdInfo.get().getFirstTokenBalance();
		final Long beforeExchangeToken2Balance = exchangeIdInfo.get().getSecondTokenBalance();
		logger.info("beforeExchangeToken1Balance" + beforeExchangeToken1Balance);
		logger.info("beforeExchangeToken2Balance" + beforeExchangeToken2Balance);

		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Long beforeToken1Balance = 0L;
		Long beforeToken2Balance = 0L;
		for (long id : firstAccount.getAssetsMap().keySet()) {
			if (assetAccountId1 == id) {
				beforeToken1Balance = firstAccount.getAssetsMap().get(id);
			}
			if (assetAccountId2 == id) {
				beforeToken2Balance = firstAccount.getAssetsMap().get(id);
			}
		}

		logger.info("before token 1 balance is " + beforeToken1Balance);
		logger.info("before token 2 balance is " + beforeToken2Balance);
		int transactionNum = 50;
		Assert.assertTrue(
				PublicMethed
						.exchangeTransaction(exchangeId, assetAccountId1, transactionNum, 1,
								exchange001Address, exchange001Key, blockingStubFull));
		firstAccount = PublicMethed.queryAccount(exchange001Address, blockingStubFull);
		Long afterToken1Balance = 0L;
		Long afterToken2Balance = 0L;
		for (long id : firstAccount.getAssetsMap().keySet()) {
			if (assetAccountId1 == id) {
				afterToken1Balance = firstAccount.getAssetsMap().get(id);
			}
			if (assetAccountId2 == id) {
				afterToken2Balance = firstAccount.getAssetsMap().get(id);
			}
		}
		logger.info("before token 1 balance is " + afterToken1Balance);
		logger.info("before token 2 balance is " + afterToken2Balance);

		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubFull);
		Long afterExchangeToken1Balance = exchangeIdInfo.get().getFirstTokenBalance();
		Long afterExchangeToken2Balance = exchangeIdInfo.get().getSecondTokenBalance();
		logger.info("afterExchangeToken1Balance" + afterExchangeToken1Balance);
		logger.info("afterExchangeToken2Balance" + afterExchangeToken2Balance);
		Assert.assertEquals(afterExchangeToken1Balance - beforeExchangeToken1Balance, beforeToken1Balance - afterToken1Balance);
		Assert.assertEquals(afterExchangeToken2Balance - beforeExchangeToken2Balance, beforeToken2Balance - afterToken2Balance);
	}

	@Test(enabled = true)
	public void test7GetExchangeListPaginated() {
		PaginatedMessage.Builder pageMessageBuilder = PaginatedMessage.newBuilder();
		pageMessageBuilder.setOffset(0);
		pageMessageBuilder.setLimit(100);
		ExchangeList exchangeList = blockingStubFull
				.getPaginatedExchangeList(pageMessageBuilder.build());
		Assert.assertTrue(exchangeList.getExchangesCount() >= 1);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
		//Solidity support getExchangeId
		exchangeIdInfo = PublicMethed.getExchange(exchangeId.toString(), blockingStubSolidity);
		logger.info("createtime is" + exchangeIdInfo.get().getCreateTime());
		Assert.assertTrue(exchangeIdInfo.get().getCreateTime() > 0);

		//Solidity support listexchange
		listExchange = PublicMethed.getExchangeList(blockingStubSolidity);
		Assert.assertTrue(listExchange.get().getExchangesCount() > 0);
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
}


