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
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;
import java.util.Map;

@Slf4j
public class ExchangeCreateActuatorTest {

	private static final String dbPath = "output_ExchangeCreate_test";
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
				ConversionUtil.McashToMatoshi(300));
		AccountCapsule ownerAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(200_000));

		dbManager.getAccountStore()
			.put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
		dbManager.getAccountStore()
			.put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
		dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
		dbManager.getDynamicPropertiesStore().saveLatestExchangeNum(0);

	}

	private Any getContract(String address, long firstTokenId, long firstTokenBalance,
							long secondTokenId, long secondTokenBalance) {
		return Any.pack(
			Contract.ExchangeCreateContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setFirstTokenId(firstTokenId)
				.setFirstTokenBalance(firstTokenBalance)
				.setSecondTokenId(secondTokenId)
				.setSecondTokenBalance(secondTokenBalance)
				.build());
	}

	@Test
	public void successExchangeCreate() {
		String firstTokenName = "abc";
		long firstTokenBalance = 100000000L;
		long firstTokenId = 1L;
		String secondTokenName = "def";
		long secondTokenBalance = 100000000L;
		long secondTokenId = 2L;

		AssetIssueCapsule assetIssueCapsule1 =
			new AssetIssueCapsule(AssetIssueContract.newBuilder()
				.setName(ByteString.copyFrom(firstTokenName.getBytes()))
				.setId(firstTokenId)
				.build());

		AssetIssueCapsule assetIssueCapsule2 =
			new AssetIssueCapsule(AssetIssueContract.newBuilder()
				.setName(ByteString.copyFrom(secondTokenName.getBytes()))
				.setId(secondTokenId)
				.build());

		dbManager.getAssetIssueStore().put(assetIssueCapsule1.getName().toByteArray(), assetIssueCapsule1);
		dbManager.getAssetIssueStore().put(assetIssueCapsule2.getName().toByteArray(), assetIssueCapsule2);

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenBalance);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10_000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.assertEquals(ret.getInstance().getExchangeId(), 1L);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			long id = 1;
			Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), id);
			ExchangeCapsule exchangeCapsuleV2 = dbManager.getExchangeStore()
				.get(ByteArray.fromLong(id));
			Assert.assertNotNull(exchangeCapsuleV2);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeCapsuleV2.getCreatorAddress());
			Assert.assertEquals(id, exchangeCapsuleV2.getId());
			Assert.assertEquals(1000000, exchangeCapsuleV2.getCreateTime());
			Assert.assertEquals(firstTokenId, exchangeCapsuleV2.getFirstTokenId());
			Assert.assertEquals(firstTokenBalance, exchangeCapsuleV2.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeCapsuleV2.getSecondTokenId());
			Assert.assertEquals(secondTokenBalance, exchangeCapsuleV2.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> getAssetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(ConversionUtil.McashToMatoshi(10_000)
					- dbManager.getDynamicPropertiesStore().getAssetIssueFee(),
				accountCapsule.getBalance());
			Assert.assertEquals(0L, getAssetV2Map.get(firstTokenId).longValue());
			Assert.assertEquals(0L, getAssetV2Map.get(secondTokenId).longValue());
		} catch (ContractValidateException | ItemNotFoundException | ContractExeException e) {
			Assert.fail();
		}
	}

	/**
	 * Init close SameTokenName, after init data,open SameTokenName
	 */
	@Test
	public void oldNotUpdateSuccessExchangeCreate2() {
		long firstTokenId = 0L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 1L;
		String secondTokenName = "abc";
		long secondTokenBalance = 100_000_000L;

		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(
			AssetIssueContract.newBuilder()
				.setName(ByteString.copyFrom(secondTokenName.getBytes()))
				.setId(secondTokenId)
				.build());
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(200_000_000));
		accountCapsule.addAsset(secondTokenId, 200_000_000L);
		accountCapsule.addAsset(1L, 200_000_000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId,
			secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			long id = 1;
			Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), id);
			ExchangeCapsule exchangeCapsule = dbManager.getExchangeStore().get(ByteArray.fromLong(id));
			Assert.assertNotNull(exchangeCapsule);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeCapsule.getCreatorAddress());
			Assert.assertEquals(id, exchangeCapsule.getId());
			Assert.assertEquals(1000000, exchangeCapsule.getCreateTime());
			secondTokenId = dbManager.getAssetIssueStore().get(secondTokenId).getId();
			Assert.assertEquals(firstTokenId, exchangeCapsule.getFirstTokenId());
			Assert.assertEquals(firstTokenBalance, exchangeCapsule.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeCapsule.getSecondTokenId());
			Assert.assertEquals(secondTokenBalance, exchangeCapsule.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> getAssetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(ConversionUtil.McashToMatoshi(200_000_000)
					- dbManager.getDynamicPropertiesStore().getAssetIssueFee() - firstTokenBalance,
				accountCapsule.getBalance());
			Assert.assertEquals(100_000_000L, getAssetV2Map.get(secondTokenId).longValue());

		} catch (ContractValidateException | ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void successExchangeCreate2() {
		long firstTokenId = 0L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 1L;
		String secondTokenName = "123";
		long secondTokenBalance = 100_000_000L;

		AssetIssueCapsule assetIssueCapsule =
			new AssetIssueCapsule(
				AssetIssueContract.newBuilder()
					.setId(secondTokenId)
					.setName(ByteString.copyFrom(secondTokenName.getBytes()))
					.build());
		dbManager.getAssetIssueStore()
			.put(assetIssueCapsule.getName().toByteArray(), assetIssueCapsule);

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(200_000_000));
		accountCapsule.addAssetAmount(secondTokenId, 200_000_000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			long id = 1;
			Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), id);
			// check V2 version
			ExchangeCapsule exchangeCapsuleV2 = dbManager.getExchangeStore()
				.get(ByteArray.fromLong(id));
			Assert.assertNotNull(exchangeCapsuleV2);
			Assert.assertEquals(ByteString.copyFrom(ownerAddress), exchangeCapsuleV2.getCreatorAddress());
			Assert.assertEquals(id, exchangeCapsuleV2.getId());
			Assert.assertEquals(1000000, exchangeCapsuleV2.getCreateTime());
			Assert.assertEquals(firstTokenId, exchangeCapsuleV2.getFirstTokenId());
			Assert.assertEquals(firstTokenBalance, exchangeCapsuleV2.getFirstTokenBalance());
			Assert.assertEquals(secondTokenId, exchangeCapsuleV2.getSecondTokenId());
			Assert.assertEquals(secondTokenBalance, exchangeCapsuleV2.getSecondTokenBalance());

			accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			Map<Long, Long> getAssetV2Map = accountCapsule.getAssetMap();
			Assert.assertEquals(ConversionUtil.McashToMatoshi(200_000_000)
					- dbManager.getDynamicPropertiesStore().getAssetIssueFee() - firstTokenBalance,
				accountCapsule.getBalance());
			Assert.assertEquals(100_000_000L, getAssetV2Map.get(secondTokenId).longValue());

		} catch (ContractValidateException | ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		}
	}


	@Test
	public void failedExchangeCreate() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100000000L;
		long secondTokenId = 2L;
		long secondTokenBalance = 100000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("First token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("First token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * SameTokenName open,second create Exchange, result is failure.
	 */
	@Test
	public void failedExchangeCreate2() {
		long firstTokenId = 0L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 1L;
		long secondTokenBalance = 100_000_000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(200_000_000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Second token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Second token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}


	@Test
	public void invalidAddress() {
		long firstTokenId = 0L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 1L;
		long secondTokenBalance = 100_000_000L;

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_INVALID, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid address");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void accountNotExist() {
		long firstTokenId = 0L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 1L;
		long secondTokenBalance = 100_000_000L;

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_NOACCOUNT, firstTokenId, firstTokenBalance, secondTokenId,
			secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + OWNER_ADDRESS_NOACCOUNT + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ADDRESS_NOACCOUNT + " does not exist",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notoEnoughBalance() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100000000L;
		long secondTokenId = 2L;
		long secondTokenBalance = 100000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenBalance);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(1000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Not enough balance for exchange create fee");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Not enough balance for exchange create fee", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void sameTokens() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100000000L;
		long secondTokenId = 1L;
		long secondTokenBalance = 100000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenBalance);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot exchange same tokens");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Cannot exchange same tokens", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * SameTokenName close,token balance less than zero
	 */
	@Test
	public void nonePositiveTokenBalance() {
		long firstTokenId = 1L;
		long firstTokenBalance = 0L;
		long secondTokenId = 2L;
		long secondTokenBalance = 0L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, 1000);
		accountCapsule.addAssetAmount(secondTokenId, 1000);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Token balance must greater than zero");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Token balance must greater than zero", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void moreThanBalanceLimit() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100_000_000_000_000_001L;
		long secondTokenId = 2L;
		long secondTokenBalance = 100000000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance);
		accountCapsule.addAssetAmount(secondTokenId, secondTokenBalance);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Token balance must less than 100000000000000000");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Token balance must less than 100000000000000000", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notEnoughBalance() {
		long firstTokenId = 0L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 1L;
		long secondTokenBalance = 100_000_000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.setBalance(firstTokenBalance + 1000L);
		accountCapsule.addAssetAmount(secondTokenId, 200_000_000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void firstTokenBalanceNotEnough() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 2L;
		long secondTokenBalance = 100_000_000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance - 1000L);
		accountCapsule.addAssetAmount(secondTokenId, 200_000_000L);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("First token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("First token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}


	@Test
	public void notEnoughBalance2() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100_000_000L;
		long secondTokenId = 0L;
		long secondTokenBalance = ConversionUtil.McashToMatoshi(100_000_000);

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.setBalance(secondTokenBalance + 1000L);
		accountCapsule.addAssetAmount(firstTokenId, 200_000_000L);
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void secondTokenBalanceNotEnough() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 2L;
		long secondTokenBalance = 100_000_000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance);
		accountCapsule.addAssetAmount(secondTokenId, 90_000_000L);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Second token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Second token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * SameTokenName close,not trx,ont token is ok, but the second one is not exist.
	 */
	@Test
	public void secondTokenNotExist() {
		long firstTokenId = 1L;
		long firstTokenBalance = 100_000_000_000000L;
		long secondTokenId = 2L;
		long secondTokenBalance = 100_000_000L;

		byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS_FIRST);
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		accountCapsule.addAssetAmount(firstTokenId, firstTokenBalance);
		accountCapsule.setBalance(ConversionUtil.McashToMatoshi(10000));
		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		ExchangeCreateActuator actuator = new ExchangeCreateActuator(getContract(
			OWNER_ADDRESS_FIRST, firstTokenId, firstTokenBalance, secondTokenId, secondTokenBalance),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestExchangeNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Second token balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Second token balance is not enough", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}