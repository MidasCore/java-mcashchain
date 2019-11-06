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
public class ExchangeInjectActuatorTest {

	private static final String dbPath = "output_ExchangeInject_test";
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
				300_000_000L);
		AccountCapsule ownerAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
				AccountType.Normal,
				200_000_000_000L);

		dbManager.getAccountStore()
			.put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
		dbManager.getAccountStore()
			.put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
		dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
	}

	private Any getContract(String address, long exchangeId, long tokenId, long quant) {
		return Any.pack(
			Contract.ExchangeInjectContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setExchangeId(exchangeId)
				.setTokenId(tokenId)
				.setQuant(quant)
				.build());
	}

	private void initExchange() {
		ExchangeCapsule exchangeCapsule =
			new ExchangeCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
				1,
				1000000,
				1L,
				2L);
		exchangeCapsule.setBalance(100000000L, 200000000L);
		ExchangeCapsule exchangeCapsule2 =
			new ExchangeCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
				2,
				1000000,
				0L,
				2L);
		exchangeCapsule2.setBalance(1_000_000_000000L, 10_000_000L);

		dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);
		dbManager.getExchangeStore().put(exchangeCapsule2.createDbKey(), exchangeCapsule2);
	}

	/**
	 * SameTokenName open, first inject Exchange,result is success.
	 */
	@Test
	public void successExchangeInject() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		String firstTokenName = "123";
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		String secondTokenName = "456";
		long secondTokenQuant = 400000000L;

		AssetIssueCapsule assetIssueCapsule1 =
			new AssetIssueCapsule(
				AssetIssueContract.newBuilder()
					.setId(firstTokenId)
					.setName(ByteString.copyFrom(firstTokenName.getBytes()))
					.build());
		dbManager.getAssetIssueStore().put(assetIssueCapsule1.getName().toByteArray(), assetIssueCapsule1);
		AssetIssueCapsule assetIssueCapsule2 =
			new AssetIssueCapsule(
				AssetIssueContract.newBuilder()
					.setId(secondTokenId)
					.setName(ByteString.copyFrom(secondTokenName.getBytes()))
					.build());
		dbManager.getAssetIssueStore().put(assetIssueCapsule2.getName().toByteArray(), assetIssueCapsule2);

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenQuant);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			long id = 1;
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore().get(ByteArray.fromLong(id));
			Assert.assertNotNull(exchangeCapsule);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeCapsule.getCreatorAddress());
			Assert.assertEquals(id, exchangeCapsule.getId());
			Assert.assertEquals(1000000, exchangeCapsule.getCreateTime());
			Assert.assertEquals(firstTokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(300000000L, exchangeCapsule.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(600000000L, exchangeCapsule.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> assetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(10000_000000L, accountCapsule.getBalance());
			Assert.assertEquals(0L, assetV2Map.get(firstTokenId).longValue());
			Assert.assertEquals(0L, assetV2Map.get(secondTokenId).longValue());

		} catch (ContractValidateException | ItemNotFoundException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void successExchangeInject2() {
		initExchange();
		long exchangeId = 2;
		long firstTokenId = 0L;
		long firstTokenQuant = 100_000_000000L;
		long secondTokenId = 2L;
		String secondTokenName = "456";
		long secondTokenQuant = 4_000_000L;
		AssetIssueCapsule assetIssueCapsule =
			new AssetIssueCapsule(
				AssetIssueContract.newBuilder()
					.setId(secondTokenId)
					.setName(ByteString.copyFrom(secondTokenName.getBytes()))
					.build());
		dbManager.getAssetIssueStore().put(assetIssueCapsule.getName().toByteArray(), assetIssueCapsule);

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(firstTokenQuant);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			Assert.assertNotNull(exchangeCapsule);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeCapsule.getCreatorAddress());
			Assert.assertEquals(exchangeId, exchangeCapsule.getId());
			Assert.assertEquals(1000000, exchangeCapsule.getCreateTime());
			Assert.assertEquals(firstTokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(1_100_000_000000L, exchangeCapsule.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(11_000_000L, exchangeCapsule.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> assetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(0L, accountCapsule.getBalance());
			Assert.assertEquals(3_000_000L, assetV2Map.get(secondTokenId).longValue());

		} catch (ContractValidateException | ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void invalidAddress() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_INVALID, exchangeId, firstTokenId, firstTokenQuant),
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
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
		}
	}

	@Test
	public void accountNotExist() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_NOACCOUNT, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("account[+OWNER_ADDRESS_NOACCOUNT+] not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
		}
	}

	@Test
	public void exchangeNotExist() {
		initExchange();
		long exchangeId = 3;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenQuant);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
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
	public void accountIsNotCreator() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_SECOND);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenQuant);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_SECOND, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("account[32548794500882809695a8a687866e76d4271a1abc] is not creator");
		} catch (ContractValidateException e) {
			Assert.assertEquals("account[32548794500882809695a8a687866e76d4271a1abc] is not creator",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void tokenIsNotInExchange() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 0L;
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(firstTokenQuant);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("token id is not in exchange");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token id is not in exchange", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void SameTokenNameOpenTokenBalanceZero() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenQuant);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			exchangeCapsule.setBalance(0, 0);
			dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Token balance in exchange is equal with 0, the exchange has been closed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Token balance in exchange is equal with 0, the exchange has been closed",
				e.getMessage());
		} catch (ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void tokenQuantLessThanZero() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = -1L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, 1000L);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("injected token quant must greater than zero");
		} catch (ContractValidateException e) {
			Assert.assertEquals("injected token quant must greater than zero", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void calculatedTokenQuantLessThanZero() {
		initExchange();
		long exchangeId = 2;
		long firstTokenId = 0L;
		long firstTokenQuant = 100L;
		long secondTokenId = 1L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(firstTokenQuant);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("the calculated token quant must be greater than 0");
		} catch (ContractValidateException e) {
			Assert.assertEquals("the calculated token quant must be greater than 0", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void tokenBalanceGreaterThanBalanceLimit() {
		initExchange();
		long exchangeId = 2;
		long firstTokenId = 0L;
		long firstTokenQuant = 100_000_000_000_000_001L;
		long secondTokenId = 1L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(firstTokenQuant);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
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
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, balance is not enough
	 */
	@Test
	public void SameTokenNameOpenBalanceNotEnough() {
		initExchange();
		long exchangeId = 2;
		long firstTokenId = 0L;
		long firstTokenQuant = 100_000000L;
		long secondTokenId = 1L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(firstTokenQuant - 1);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
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
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void tokenBalanceNotEnough() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenQuant - 1);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, firstTokenQuant),
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
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void balanceNotEnough2() {
		initExchange();
		long exchangeId = 2;
		long secondTokenId = 2L;
		long secondTokenQuant = 4000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(399_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, secondTokenId, secondTokenQuant),
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
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void anotherTokenBalanceNotEnough() {
		initExchange();
		long exchangeId = 1;
		long firstTokenId = 1L;
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		long secondTokenQuant = 400000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenQuant - 1);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenQuant);
		accountCapsule.setBalance(10000_000000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, secondTokenId, secondTokenQuant),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("another token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("another token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	@Test
	public void invalidParam() {
		initExchange();
		long firstTokenId = 5L;
		long exchangeId = 1;
		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
			OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, 1000),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("token id is not in exchange");
		} catch (ContractValidateException e) {
			Assert.assertEquals("token id is not in exchange", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
		}
	}

}