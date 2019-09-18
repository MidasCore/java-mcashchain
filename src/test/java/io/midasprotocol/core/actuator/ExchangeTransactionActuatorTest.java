package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.ExchangeCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;
import java.util.Map;

@Slf4j
public class ExchangeTransactionActuatorTest {

	private static final String dbPath = "output_exchange_transaction_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String OWNER_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String OWNER_ADDRESS_SECOND;
	private static final String URL = "https://mcash.network";
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ADDRESS_NOACCOUNT;
	private static final String OWNER_ADDRESS_BALANCENOTSUFFIENT;
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS_FIRST = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		OWNER_ADDRESS_SECOND = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		OWNER_ADDRESS_NOACCOUNT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
		OWNER_ADDRESS_BALANCENOTSUFFIENT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e06d4271a1ced";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
	}

	/**
	 * Release resources.
	 */
	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	/**
	 * create temp Capsule test need.
	 */
	@Before
	public void initTest() {
		AccountCapsule ownerAccountFirstCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_FIRST),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
				AccountType.Normal,
				10_000_000_000L);
		AccountCapsule ownerAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
				AccountType.Normal,
				20_000_000_000L);

		dbManager.getAccountStore()
			.put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
		dbManager.getAccountStore()
			.put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
		dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
	}

	private Any getContract(String address, long exchangeId, long tokenId,
							long quant, long expected) {
		return Any.pack(
			Contract.ExchangeTransactionContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setExchangeId(exchangeId)
				.setTokenId(tokenId)
				.setQuant(quant)
				.setExpected(expected)
				.build());
	}

	private void initExchange() {
		AssetIssueCapsule assetIssueCapsule1 = new AssetIssueCapsule(
			AssetIssueContract.newBuilder()
				.setName(ByteString.copyFrom("123".getBytes()))
				.setId(1L)
				.build());
		AssetIssueCapsule assetIssueCapsule2 =
			new AssetIssueCapsule(
				AssetIssueContract.newBuilder()
					.setName(ByteString.copyFrom("456".getBytes()))
					.setId(2)
					.build());
		dbManager.getAssetIssueStore().put(assetIssueCapsule1.createDbKey(),
			assetIssueCapsule1);
		dbManager.getAssetIssueStore().put(assetIssueCapsule2.createDbKey(),
			assetIssueCapsule2);

		ExchangeCapsule exchangeCapsule =
			new ExchangeCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
				1,
				1000000,
				0L,
				1L);
		exchangeCapsule.setBalance(1_000_000_000_000L, 10_000_000L); // 1M TRX == 10M abc
		ExchangeCapsule exchangeCapsule2 =
			new ExchangeCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
				2,
				1000000,
				1L,
				2L);
		exchangeCapsule2.setBalance(100000000L, 200000000L);
		dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);
		dbManager.getExchangeStore().put(exchangeCapsule2.createDbKey(), exchangeCapsule2);
	}

	@Test
	public void successExchangeTransaction() {
		initExchange();
		long exchangeId = 1;
		long tokenId = 0L;
		long quant = 100_000_000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(2L));

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore()
				.get(ByteArray.fromLong(exchangeId));
			Assert.assertNotNull(exchangeCapsule);
			long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
			long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

			Assert.assertEquals(exchangeId, exchangeCapsule.getId());
			Assert.assertEquals(tokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(1_000_000_000_000L, firstTokenBalance);
			Assert.assertEquals(1L, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(10_000_000L, secondTokenBalance);

			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			exchangeCapsule = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			Assert.assertNotNull(exchangeCapsule);
			Assert.assertEquals(exchangeId, exchangeCapsule.getId());
			Assert.assertEquals(1000000, exchangeCapsule.getCreateTime());
			Assert.assertEquals(tokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(firstTokenBalance + quant, exchangeCapsule.getFirstTokenBalance());
			Assert.assertEquals(1L, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(9999001L, exchangeCapsule.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			assetMap = accountCapsule.getAssetMap();
			Assert.assertEquals(20000_000000L - quant, accountCapsule.getBalance());
			Assert.assertEquals(999L, assetMap.get(1L).longValue());

			Assert.assertEquals(999L, ret.getExchangeReceivedAmount());

		} catch (ContractValidateException | ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void successExchangeTransaction2() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore()
				.get(ByteArray.fromLong(exchangeId));
			Assert.assertNotNull(exchangeCapsule);
			long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
			long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

			Assert.assertEquals(exchangeId, exchangeCapsule.getId());
			Assert.assertEquals(tokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(100000000L, firstTokenBalance);
			Assert.assertEquals(2L, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(200000000L, secondTokenBalance);

			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);

			exchangeCapsule = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			Assert.assertNotNull(exchangeCapsule);
			Assert.assertEquals(exchangeId, exchangeCapsule.getId());
			Assert.assertEquals(1000000, exchangeCapsule.getCreateTime());
			Assert.assertEquals(tokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(firstTokenBalance + quant, exchangeCapsule.getFirstTokenBalance());
			Assert.assertEquals(2L, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(199998001L, exchangeCapsule.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			assetMap = accountCapsule.getAssetMap();
			Assert.assertEquals(9000L, assetMap.get(1L).longValue());
			Assert.assertEquals(1999L, assetMap.get(2L).longValue());

		} catch (ContractValidateException | ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void invalidAddress() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_INVALID, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid address");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void accountNotExist() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_NOACCOUNT, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void exchangeNotExist() {
		initExchange();
		long exchangeId = 3;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Exchange not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Exchange[3] not exists", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void tokenIsNotInExchange() {
		initExchange();
		long exchangeId = 1;
		long tokenId = 10L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("token is not in exchange");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token is not in exchange", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void zeroTokenBalance() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore()
				.get(ByteArray.fromLong(exchangeId));
			exchangeCapsule.setBalance(0, 0);
			dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Token balance in exchange is equal with 0,"
				+ "the exchange has been closed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Token balance in exchange is equal with 0,"
					+ "the exchange has been closed",
				e.getMessage());
		} catch (ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void tokenQuantLessThanZero() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = -1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("token quant must greater than zero");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token quant must greater than zero", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void tokenBalanceGreaterThanBalanceLimit() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 100_000_000_000_000_001L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, 10000);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("token balance must less than 100000000000000000");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token balance must less than 100000000000000000", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void balanceNotEnough() {
		initExchange();
		long exchangeId = 1;
		long tokenId = 0L;
		long quant = 100_000000L;
		long buyTokenId = 1L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		Map<Long, Long> assetMap = accountCapsule.getAssetMap();
		accountCapsule.setBalance(quant - 1);
		Assert.assertFalse(assetMap.containsKey(buyTokenId));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void tokenBalanceNotEnough() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, quant - 1);
		Map<Long, Long> assetV2Map = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetV2Map.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void tokenRequiredNotEnough() {
		initExchange();
		long exchangeId = 2;
		long tokenId = 1L;
		long quant = 1_000L;
		long buyTokenId = 2L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(tokenId, quant);
		Map<Long, Long> assetV2Map = accountCapsule.getAssetMap();
		Assert.assertEquals(20000_000000L, accountCapsule.getBalance());
		Assert.assertFalse(assetV2Map.containsKey(buyTokenId));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		long expected = 0;
		try {
			ExchangeCapsule exchangeCapsuleV2 = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			expected = exchangeCapsuleV2.transaction(tokenId, quant);
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		}

		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, tokenId, quant, expected + 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("should not run here");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token required must greater than expected", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	@Test
	public void invalidParam() {
		initExchange();
		long exchangeId = 1;
		long quant = 100_000_000L; // use 100 TRX to buy abc
		TransactionResultCapsule ret = new TransactionResultCapsule();

		//token id is not a valid number
		ExchangeTransactionActuator actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, 10L, quant, 1),
			dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("should not run here");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token is not in exchange", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//token expected must greater than zero
		actuator = new ExchangeTransactionActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, 0L, quant, 0),
			dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("should not run here");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token expected must greater than zero", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}
}