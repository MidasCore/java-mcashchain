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

import static org.testng.Assert.fail;

@Slf4j
public class ExchangeInjectActuatorTest {

	private static final String dbPath = "output_ExchangeInject_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String OWNER_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String OWNER_ADDRESS_SECOND;
	private static final String URL = "https://tron.network";
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

	private void InitExchangeSameTokenNameActive() {

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
	public void SameTokenNameOpenSuccessExchangeInject() {
		InitExchangeSameTokenNameActive();
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
			ExchangeCapsule exchangeCapsuleV2 = dbManager.getExchangeStore().get(ByteArray.fromLong(id));
			Assert.assertNotNull(exchangeCapsuleV2);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeCapsuleV2.getCreatorAddress());
			Assert.assertEquals(id, exchangeCapsuleV2.getID());
			Assert.assertEquals(1000000, exchangeCapsuleV2.getCreateTime());
			Assert.assertEquals(firstTokenId, exchangeCapsuleV2.getFirstTokenId());
			Assert.assertEquals(300000000L, exchangeCapsuleV2.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeCapsuleV2.getSecondTokenId());
			Assert.assertEquals(600000000L, exchangeCapsuleV2.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> assetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(10000_000000L, accountCapsule.getBalance());
			Assert.assertEquals(0L, assetV2Map.get(firstTokenId).longValue());
			Assert.assertEquals(0L, assetV2Map.get(secondTokenId).longValue());

		} catch (ContractValidateException e) {
			logger.info(e.getMessage());
			Assert.assertFalse(e instanceof ContractValidateException);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} catch (ItemNotFoundException e) {
			Assert.assertFalse(e instanceof ItemNotFoundException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, second inject Exchange,result is success.
	 */
	@Test
	public void SameTokenNameOpenSuccessExchangeInject2() {
		InitExchangeSameTokenNameActive();
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
			ExchangeCapsule exchangeV2Capsule = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			Assert.assertNotNull(exchangeV2Capsule);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeV2Capsule.getCreatorAddress());
			Assert.assertEquals(exchangeId, exchangeV2Capsule.getID());
			Assert.assertEquals(1000000, exchangeV2Capsule.getCreateTime());
			Assert.assertEquals(firstTokenId, exchangeV2Capsule.getFirstTokenId());
			Assert.assertEquals(1_100_000_000000L, exchangeV2Capsule.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeV2Capsule.getSecondTokenId());
			Assert.assertEquals(11_000_000L, exchangeV2Capsule.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> assetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(0L, accountCapsule.getBalance());
			Assert.assertEquals(3_000_000L, assetV2Map.get(secondTokenId).longValue());

		} catch (ContractValidateException e) {
			logger.info(e.getMessage());
			Assert.assertFalse(e instanceof ContractValidateException);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} catch (ItemNotFoundException e) {
			Assert.assertFalse(e instanceof ItemNotFoundException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, use Invalid Address, result is failed, exception is "Invalid address".
	 */
	@Test
	public void SameTokenNameOpenInvalidAddress() {
		InitExchangeSameTokenNameActive();
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
			fail("Invalid address");
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
		}
	}

	/**
	 * SameTokenName open, use AccountStore not exists, result is failed, exception is "account not
	 * exists".
	 */
	@Test
	public void SameTokenNameOpenNoAccount() {
		InitExchangeSameTokenNameActive();
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
			fail("account[+OWNER_ADDRESS_NOACCOUNT+] not exists");
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
		}
	}

	/**
	 * SameTokenName open, Exchange not exists
	 */
	@Test
	public void SameTokenNameOpenExchangeNotExist() {
		InitExchangeSameTokenNameActive();
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
			fail("Exchange not exists");
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Exchange[3] not exists",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(1L));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(2L));
		}
	}

	/**
	 * SameTokenName open, account[" + readableOwnerAddress + "] is not creator
	 */
	@Test
	public void SameTokenNameOpenAccountIsNotCreator() {
		InitExchangeSameTokenNameActive();
		long exchangeId = 1;
		long firstTokenId = 1L;
		String firstTokenName = "123";
		long firstTokenQuant = 200000000L;
		long secondTokenId = 2L;
		String secondTokenName = "456";
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("account[32548794500882809695a8a687866e76d4271a1abc] is not creator",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, token is not in exchange
	 */
	@Test
	public void SameTokenNameOpenTokenIsNotInExchange() {
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("token id is not in exchange",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, Token balance in exchange is equal with 0, the exchange has been closed"
	 */
	@Test
	public void SameTokenNameOpenTokenBalanceZero() {
		InitExchangeSameTokenNameActive();
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
			ExchangeCapsule exchangeCapsuleV2 = dbManager.getExchangeStore().get(ByteArray.fromLong(exchangeId));
			exchangeCapsuleV2.setBalance(0, 0);
			dbManager.getExchangeStore().put(exchangeCapsuleV2.createDbKey(), exchangeCapsuleV2);

			actuator.validate();
			actuator.execute(ret);
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Token balance in exchange is equal with 0, the exchange has been closed",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} catch (ItemNotFoundException e) {
			Assert.assertFalse(e instanceof ItemNotFoundException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, injected token quant must greater than zero
	 */
	@Test
	public void SameTokenNameOpenTokenQuantLessThanZero() {
		InitExchangeSameTokenNameActive();
		long exchangeId = 1;
		long firstTokenId = 1L;
		String firstTokenName = "123";
		long firstTokenQuant = -1L;
		long secondTokenId = 2L;
		String secondTokenName = "456";
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("injected token quant must greater than zero",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, "the calculated token quant  must be greater than 0"
	 */
	@Test
	public void SameTokenNameOpenCalculatedTokenQuantLessThanZero() {
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("the calculated token quant  must be greater than 0",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			;
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, token balance must less than balanceLimit
	 */
	@Test
	public void SameTokenNameOpenTokenBalanceGreaterThanBalanceLimit() {
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("token balance must less than 100000000000000000",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
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
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("balance is not enough",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, first token balance is not enough
	 */
	@Test
	public void SameTokenNameOpenTokenBalanceNotEnough() {
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("token balance is not enough",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, balance is not enough2
	 */
	@Test
	public void SameTokenNameOpenBalanceNotEnough2() {
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("balance is not enough",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, first token balance is not enough
	 */
	@Test
	public void SameTokenNameOpenAnotherTokenBalanceNotEnough() {
		InitExchangeSameTokenNameActive();
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
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("another token balance is not enough",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
			dbManager.getExchangeStore().delete(ByteArray.fromLong(secondTokenId));
		}
	}

	/**
	 * SameTokenName open, invalid param "token id is not a valid number"
	 */
	@Test
	public void SameTokenNameOpenInvalidParam() {
		InitExchangeSameTokenNameActive();
		long firstTokenId = 5L;
		long exchangeId = 1;
		ExchangeInjectActuator actuator = new ExchangeInjectActuator(getContract(
				OWNER_ADDRESS_FIRST, exchangeId, firstTokenId, 1000),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("token id is not in exchange", e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		} finally {
			dbManager.getExchangeStore().delete(ByteArray.fromLong(firstTokenId));
		}
	}

}