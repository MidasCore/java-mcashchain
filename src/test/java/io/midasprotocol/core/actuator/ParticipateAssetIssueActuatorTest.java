package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import org.joda.time.DateTime;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

public class ParticipateAssetIssueActuatorTest {

	private static final Logger logger = LoggerFactory.getLogger("Test");
	private static final String dbPath = "output_participate_asset_test";
	private static final String OWNER_ADDRESS;
	private static final String TO_ADDRESS;
	private static final String TO_ADDRESS_2;
	private static final String THIRD_ADDRESS;
	private static final String NOT_EXIT_ADDRESS;
	private static final String ASSET_NAME = "myCoin";
	private static final long OWNER_BALANCE = 99999;
	private static final long TO_BALANCE = 100001;
	private static final long TOTAL_SUPPLY = 10000000000000L;
	private static final int TRX_NUM = 2;
	private static final int NUM = 2147483647;
	private static final int VOTE_SCORE = 2;
	private static final String DESCRIPTION = "MCASH";
	private static final String URL = "https://mcash.network";
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1234";
		TO_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		TO_ADDRESS_2 = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e048892";
		THIRD_ADDRESS = Wallet.getAddressPreFixString() + "4948c2e8a756d9437037dcd8c7e0c73d560ca38d";
		NOT_EXIT_ADDRESS = Wallet.getAddressPreFixString() + "B56446E617E924805E4D6CA021D341FEF6E2013B";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(1000000);
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
	public void createCapsule() {
		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				OWNER_BALANCE);
		AccountCapsule toAccountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("toAccount"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
				AccountType.Normal,
				TO_BALANCE);
		AccountCapsule toAccountCapsule2 =
			new AccountCapsule(
				ByteString.copyFromUtf8("toAccount2"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS_2)),
				AccountType.Normal,
				TO_BALANCE);

		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
		dbManager.getAccountStore()
			.put(toAccountCapsule2.getAddress().toByteArray(), toAccountCapsule2);
	}

	private boolean isNullOrZero(Long value) {
		return null == value || value == 0;
	}

	private Any getContract(long count) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

		return Any.pack(
			Contract.ParticipateAssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAssetId(tokenIdNum)
				.setAmount(count)
				.build());
	}

	private Any getContractWithOwner(long count, String ownerAddress) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

		return Any.pack(
			Contract.ParticipateAssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAssetId(tokenIdNum)
				.setAmount(count)
				.build());
	}

	private Any getContractWithTo(long count, String toAddress) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		return Any.pack(
			Contract.ParticipateAssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(toAddress)))
				.setAssetId(tokenIdNum)
				.setAmount(count)
				.build());
	}

	private Any getContract(long count, long assetId) {
		return Any.pack(
			Contract.ParticipateAssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAssetId(assetId)
				.setAmount(count)
				.build());
	}

	private void initAssetIssue(long startTimestamp, long endTimestamp) {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
				.setId(id)
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(startTimestamp)
				.setEndTime(endTimestamp)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();

		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		AccountCapsule toAccountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(TO_ADDRESS));
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		toAccountCapsule.addAsset(id, TOTAL_SUPPLY);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	private void initAssetIssue(long startTimestamp, long endTimestamp, String assetName) {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);

		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString(assetName)))
				.setId(id)
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(startTimestamp)
				.setEndTime(endTimestamp)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		AccountCapsule toAccountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(TO_ADDRESS));
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		toAccountCapsule.addAsset(id, TOTAL_SUPPLY);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	private void initAssetIssueWithOwner(long startTimestamp, long endTimestamp, String owner) {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);

		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owner)))
				.setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setId(id)
				.setNum(NUM)
				.setStartTime(startTimestamp)
				.setEndTime(endTimestamp)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		AccountCapsule toAccountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(TO_ADDRESS));
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		toAccountCapsule.addAsset(id, TOTAL_SUPPLY);

		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	@Test
	public void rightAssetIssue() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		ParticipateAssetIssueActuator actuator =
			new ParticipateAssetIssueActuator(getContract(1000L), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE - 1000);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE + 1000);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMap().get(id).longValue(), (1000L) / TRX_NUM * NUM);
			Assert.assertEquals(
				toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY - (1000L) / TRX_NUM * NUM);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void assetIssueTimeRight() {
		DateTime now = DateTime.now();
		initAssetIssue(now.minusDays(1).getMillis(), now.getMillis());
		ParticipateAssetIssueActuator actuator =
			new ParticipateAssetIssueActuator(getContract(1000L), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No longer valid period");
		} catch (ContractValidateException e) {
			Assert.assertEquals("No longer valid period", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void assetIssueTimeLeft() {
		DateTime now = DateTime.now();
		initAssetIssue(now.minusDays(1).getMillis(), now.getMillis());
		ParticipateAssetIssueActuator actuator =
			new ParticipateAssetIssueActuator(getContract(1000L), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No longer valid period");
		} catch (ContractValidateException e) {
			Assert.assertEquals("No longer valid period", e.getMessage());
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void exchangeDivisibleTest() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		ParticipateAssetIssueActuator actuator =
			new ParticipateAssetIssueActuator(getContract(999L), dbManager); //no problem
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMap().get(id).longValue(),
				(999L * NUM) / TRX_NUM);
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(),
				TOTAL_SUPPLY - (999L * NUM) / TRX_NUM);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void negativeAmountTest() {
		DateTime now = DateTime.now();
		initAssetIssue(now.minusDays(1).getMillis(), now.plusDays(1).getMillis());
		ParticipateAssetIssueActuator actuator =
			new ParticipateAssetIssueActuator(getContract(-999L), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Amount must greater than 0");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Amount must greater than 0", e.getMessage());

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void zeroAmount() {
		DateTime now = DateTime.now();
		initAssetIssue(now.minusDays(1).getMillis(), now.plusDays(1).getMillis());
		ParticipateAssetIssueActuator actuator =
			new ParticipateAssetIssueActuator(getContract(0), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Amount must greater than 0");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Amount must greater than 0", e.getMessage());

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notExistOwner() {
		DateTime now = DateTime.now();
		initAssetIssue(now.minusDays(1).getMillis(), now.plusDays(1).getMillis());
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContractWithOwner(101, NOT_EXIT_ADDRESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account does not exist", e.getMessage());
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notExistToAccount() {
		initAssetIssueWithOwner(
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000,
			NOT_EXIT_ADDRESS);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContractWithTo(101, NOT_EXIT_ADDRESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("To account does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("To account does not exist", e.getMessage());

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void selfParticipateAsset() {
		initAssetIssueWithOwner(
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000,
			OWNER_ADDRESS);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContractWithTo(101, OWNER_ADDRESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot participate asset issue yourself");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Cannot participate asset issue yourself", e.getMessage());

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void participateAssetToThird() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContractWithTo(101, THIRD_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("The asset is not issued by " + THIRD_ADDRESS);
		} catch (ContractValidateException e) {
			Assert.assertEquals("The asset is not issued by " + THIRD_ADDRESS, e.getMessage());

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notEnoughMcash() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		// First, reduce the owner trx balance. Else can't complete this test case.
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setBalance(100);
		dbManager.getAccountStore().put(owner.getAddress().toByteArray(), owner);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(getContract(101),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Not enough balance");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Not enough balance", e.getMessage());

			owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), 100);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notEnoughAsset() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		// First, reduce to account asset balance. Else can't complete this test case.
		AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();

		toAccount.reduceAssetAmount(id, TOTAL_SUPPLY - 10000);
		dbManager.getAccountStore().put(toAccount.getAddress().toByteArray(), toAccount);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(getContract(1000),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Asset balance is not enough");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Asset balance is not enough", e.getMessage());

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);

			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(), 10000);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noneExistAssetTest() {
		DateTime now = DateTime.now();
		initAssetIssue(now.minusDays(1).getMillis(), now.plusDays(1).getMillis());
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContract(1, 100L),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No asset named with id " + 100);
		} catch (ContractValidateException e) {
			Assert.assertEquals(("No asset with id 100"), e.getMessage());

			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void addOverflowTest() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		// First, increase the owner asset balance. Else can't complete this test case.
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		owner.addAsset(id, Long.MAX_VALUE);
		dbManager.getAccountStore().put(owner.getAddress().toByteArray(), owner);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContract(1L),
			dbManager);
		//NUM = 2147483647;
		//ASSET_BLANCE = Long.MAX_VALUE + 2147483647/2
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("long overflow");
		} catch (ContractValidateException e) {
			Assert.fail(e.getMessage());
		} catch (ContractExeException e) {
			Assert.assertEquals("long overflow", e.getMessage());

			owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);

			Assert.assertEquals(owner.getAssetMap().get(id).longValue(),
				Long.MAX_VALUE);
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(),
				TOTAL_SUPPLY);
		}
	}

	@Test
	public void multiplyOverflowTest() {
		initAssetIssue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - 1000,
			dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 1000);
		// First, increase the owner trx balance. Else can't complete this test case.
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setBalance(ConversionUtil.McashToMatoshi(100000000L));
		dbManager.getAccountStore().put(owner.getAddress().toByteArray(), owner);
		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContract(8589934597L),
			dbManager);
		//NUM = 2147483647;
		//LONG_MAX = 9223372036854775807L = 0x7fffffffffffffff
		//4294967298 * 2147483647 = 9223372036854775806 = 0x7ffffffffffffffe
		//8589934596 * 2147483647 = 4294967298 * 2147483647 *2 = 0xfffffffffffffffc = -4
		//8589934597 * 2147483647 = 8589934596 * 2147483647 + 2147483647 = -4 + 2147483647 = 2147483643  vs 9223372036854775806*2 + 2147483647

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("long overflow");
		} catch (ContractValidateException e) {
			Assert.assertEquals("long overflow", e.getMessage());

			owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), ConversionUtil.McashToMatoshi(100000000L));
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			long id = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(owner.getAssetMap().get(id)));
			Assert.assertEquals(toAccount.getAssetMap().get(id).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void exchangeAmountTest() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(tokenId);
		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS_2)))
				.setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(100)
				.setId(tokenId)
				.setNum(1)
				.setStartTime(dbManager.getHeadBlockTimeStamp() - 10000)
				.setEndTime(dbManager.getHeadBlockTimeStamp() + 11000000)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		AccountCapsule toAccountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(TO_ADDRESS_2));
		toAccountCapsule.addAsset(tokenId, TOTAL_SUPPLY);

		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(),
			toAccountCapsule);
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setBalance(ConversionUtil.McashToMatoshi(100000000L));
		dbManager.getAccountStore().put(owner.getAddress().toByteArray(), owner);

		ParticipateAssetIssueActuator actuator = new ParticipateAssetIssueActuator(
			getContractWithTo(1, TO_ADDRESS_2),
			dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Can not process the exchange");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Can not process the exchange", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}
