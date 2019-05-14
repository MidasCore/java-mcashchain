package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.AssetIssueCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.Parameter;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Contract.AssetIssueContract.FrozenSupply;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
public class AssetIssueActuatorTest {

	private static final String dbPath = "output_asset_issue_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_SECOND;
	private static final String NAME = "trx-my";
	private static final long TOTAL_SUPPLY = 10000L;
	private static final int TRX_NUM = 10000;
	private static final int NUM = 100000;
	private static final String DESCRIPTION = "myCoin";
	private static final String URL = "tron-my.com";
	private static final String ASSET_NAME_SECOND = "asset_name2";
	private static TronApplicationContext context;
	private static Manager dbManager;
	private static long now = 0;
	private static long startTime = 0;
	private static long endTime = 0;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049150";
		OWNER_ADDRESS_SECOND = "548794500882809695a8a687866e76d4271a1abc";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		//    Args.setParam(new String[]{"--output-directory", dbPath},
		//        "config-junit.conf");
		//    dbManager = new Manager();
		//    dbManager.init();
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
						dbManager.getDynamicPropertiesStore().getAssetIssueFee());
		AccountCapsule ownerSecondCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("ownerSecond"),
						ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
						AccountType.Normal,
						dbManager.getDynamicPropertiesStore().getAssetIssueFee());
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().put(
				ownerSecondCapsule.getAddress().toByteArray(), ownerSecondCapsule);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(Parameter.TimeConstant.MS_PER_DAY);
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);

		now = dbManager.getHeadBlockTimeStamp();
		startTime = now + 2 * Parameter.TimeConstant.MS_PER_DAY;
		endTime = now + 3 * Parameter.TimeConstant.MS_PER_DAY;
	}

	@After
	public void removeCapsule() {
		byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);
		dbManager.getAccountStore().delete(address);
	}

	private Any getContract() {
		long nowTime = new Date().getTime();
		return Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(6)
						.build());
	}

	/**
	 * SameTokenName close, asset issue success
	 */
	@Test
	public void SameTokenNameCloseAssetIssueSuccess() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			// check V1
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteString.copyFromUtf8(NAME).toByteArray());
			Assert.assertNotNull(assetIssueCapsule);
			Assert.assertEquals(6, assetIssueCapsule.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsule.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsule.getTrxNum());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
			// check V2
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 =
					dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum)));
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(0, assetIssueCapsuleV2.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getTrxNum());
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
					TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}


	/**
	 * Init close SameTokenName,after init data,open SameTokenName
	 */
	@Test
	public void oldNotUpdateAssetIssueSuccess() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			// V1,Data is no longer update
			Assert.assertFalse(
					dbManager.getAssetIssueStore().has(ByteString.copyFromUtf8(NAME).toByteArray()));
			// check V2
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 =
					dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum)));
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getTrxNum());
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
					TOTAL_SUPPLY);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * SameTokenName open, asset issue success
	 */
	@Test
	public void SameTokenNameOpenAssetIssueSuccess() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			// V1,Data is no longer update
			Assert.assertFalse(
					dbManager.getAssetIssueStore().has(ByteString.copyFromUtf8(NAME).toByteArray()));
			//V2
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			byte[] assertKey = ByteArray.fromString(String.valueOf(tokenIdNum));
			AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store().get(assertKey);
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getTrxNum());
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
					TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Total supply must greater than zero. Else can't asset issue and balance do not change.
	 */
	@Test
	public void negativeTotalSupplyTest() {
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(-TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("TotalSupply must greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("TotalSupply must greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Total supply must greater than zero. Else can't asset issue and balance do not change.
	 */
	@Test
	public void zeroTotalSupplyTest() {
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(0)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("TotalSupply must greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("TotalSupply must greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Trx num must greater than zero. Else can't asset issue and balance do not change.
	 */
	@Test
	public void negativeTrxNumTest() {
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(-TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("TrxNum must greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("TrxNum must greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Trx num must greater than zero. Else can't asset issue and balance do not change.
	 */
	@Test
	public void zeroTrxNumTest() {
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(0)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("TrxNum must greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("TrxNum must greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Num must greater than zero. Else can't asset issue and balance do not change.
	 */
	@Test
	public void negativeNumTest() {
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(-NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Num must greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Num must greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Trx num must greater than zero. Else can't asset issue and balance do not change.
	 */
	@Test
	public void zeroNumTest() {
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(0)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Num must greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Num must greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Asset name length must between 1 to 32 and can not contain space and other unreadable character, and can not contain chinese characters.
	 */
	@Test
	public void assetNameTest() {
		long nowTime = new Date().getTime();

		//Empty name, throw exception
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.EMPTY)
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid assetName");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid assetName", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		// Too long name, throw exception. Max long is 32.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklmo"))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid assetName");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid assetName", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		// Contain space, throw exception. Every character need readable.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8("t e"))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid assetName");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid assetName", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		// Contain chinese character, throw exception.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95")))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid assetName");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid assetName", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		// 32 byte readable character just ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklm"))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get("testname0123456789abcdefghijgklm".getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get("testname0123456789abcdefghijgklm").longValue(),
					TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		createCapsule();
		// 1 byte readable character ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8("0"))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get("0".getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get("0").longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Url length must between 1 to 256.
	 */
	@Test
	public void urlTest() {
		long nowTime = new Date().getTime();

		//Empty url, throw exception
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.EMPTY)
				.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" +
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789" +
				"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		//Too long url, throw exception. Max long is 256.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(url256Bytes + "0"))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		// 256 byte readable character just ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(url256Bytes))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(NAME.getBytes());
			Assert.assertNotNull(assetIssueCapsule);
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(),
					TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		createCapsule();
		// 1 byte url.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8("0"))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(NAME.getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		createCapsule();
		// 1 byte space ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(" "))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(NAME.getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Description length must less than 200.
	 */
	@Test
	public void descriptionTest() {
		long nowTime = new Date().getTime();

		String description200Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789" +
				"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789" +
				"abcdef0123456789abcdef01234567";
		//Too long description, throw exception. Max long is 200.
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(description200Bytes + "0"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid description");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid description", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		// 200 bytes character just ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(description200Bytes))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(NAME.getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(),
					TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		createCapsule();
		// Empty description is ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.EMPTY)
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(NAME.getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		createCapsule();
		// 1 byte space ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(" "))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(NAME.getBytes());
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * Test FrozenSupply, 1. frozen_amount must greater than zero.
	 */
	@Test
	public void frozenTest() {
		//frozen_amount = 0 throw exception.
		FrozenSupply frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(0)
				.build();
		long nowTime = new Date().getTime();
		Any contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.addFrozenSupply(frozenSupply)
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Frozen supply must be greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Frozen supply must be greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//frozen_amount < 0 throw exception.
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(-1)
				.build();
		contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.addFrozenSupply(frozenSupply)
						.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Frozen supply must be greater than 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Frozen supply must be greater than 0!", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		long minFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMinFrozenSupplyTime();
		long maxFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyTime();

		//FrozenDays = 0 throw exception.
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(0).setFrozenAmount(1)
				.build();
		nowTime = new Date().getTime();
		contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.addFrozenSupply(frozenSupply)
						.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
					+ minFrozenSupplyTime + " days");
		} catch (ContractValidateException e) {
			Assert.assertEquals(
					"frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
							+ minFrozenSupplyTime + " days", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//FrozenDays < 0 throw exception.
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(-1).setFrozenAmount(1)
				.build();
		contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.addFrozenSupply(frozenSupply)
						.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
					+ minFrozenSupplyTime + " days");
		} catch (ContractValidateException e) {
			Assert.assertEquals(
					"frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
							+ minFrozenSupplyTime + " days", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//FrozenDays >  maxFrozenSupplyTime throw exception.
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(maxFrozenSupplyTime + 1)
				.setFrozenAmount(1)
				.build();
		contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.addFrozenSupply(frozenSupply)
						.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
					+ minFrozenSupplyTime + " days");
		} catch (ContractValidateException e) {
			Assert.assertEquals(
					"frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
							+ minFrozenSupplyTime + " days", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
					dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
					dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//frozen_amount = 1 and  frozenDays = 1 is OK
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(1)
				.build();
		contract = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.addFrozenSupply(frozenSupply)
						.build());

		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * 1. start time should not be null 2. end time should not be null 3. start time >=
	 * getHeadBlockTimeStamp 4. start time < end time
	 */
	@Test
	public void issueTimeTest() {
		//empty start time will throw exception
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Start time should be not empty");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Start time should be not empty", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//empty end time will throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("End time should be not empty");
		} catch (ContractValidateException e) {
			Assert.assertEquals("End time should be not empty", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//startTime == now, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(now)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Start time should be greater than HeadBlockTime");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Start time should be greater than HeadBlockTime", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//startTime < now, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(now - 1)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Start time should be greater than HeadBlockTime");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Start time should be greater than HeadBlockTime", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//endTime == startTime, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(startTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("End time should be greater than start time");
		} catch (ContractValidateException e) {
			Assert.assertEquals("End time should be greater than start time", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//endTime < startTime, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(endTime)
				.setEndTime(startTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("End time should be greater than start time");
		} catch (ContractValidateException e) {
			Assert.assertEquals("End time should be greater than start time", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//right issue, will not throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			AccountCapsule account = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			Assert.assertEquals(account.getAssetIssuedName().toStringUtf8(), NAME);
			Assert.assertEquals(account.getAssetMap().size(), 1);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * an account should issue asset only once
	 */
	@Test
	public void assetIssueNameTest() {
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(ASSET_NAME_SECOND))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		actuator = new AssetIssueActuator(contract, dbManager);
		ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("An account can only issue one asset");
		} catch (ContractValidateException e) {
			Assert.assertEquals("An account can only issue one asset", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(ASSET_NAME_SECOND));
		}
	}

	@Test
	public void assetIssueTRXNameTest() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8("MCASH"))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.build());
		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Asset name can't be mcash");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Asset name can't be mcash", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(ASSET_NAME_SECOND));
		}
	}

	@Test
	public void frozenListSizeTest() {
		dbManager.getDynamicPropertiesStore().saveMaxFrozenSupplyNumber(3);
		List<FrozenSupply> frozenList = new ArrayList<>();
		for (int i = 0; i < dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyNumber() + 2; i++) {
			frozenList.add(FrozenSupply.newBuilder()
					.setFrozenAmount(10)
					.setFrozenDays(3)
					.build());
		}
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.addAllFrozenSupply(frozenList)
				.build());
		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Frozen supply list length is too long");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Frozen supply list length is too long", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	@Test
	public void frozenSupplyMoreThanTotalSupplyTest() {
		dbManager.getDynamicPropertiesStore().saveMaxFrozenSupplyNumber(3);
		List<FrozenSupply> frozenList = new ArrayList<>();
		frozenList.add(FrozenSupply.newBuilder()
				.setFrozenAmount(TOTAL_SUPPLY + 1)
				.setFrozenDays(3)
				.build());
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setTrxNum(TRX_NUM).setNum(NUM)
				.setStartTime(startTime)
				.setEndTime(endTime)
				.setDescription(ByteString.copyFromUtf8("description"))
				.setUrl(ByteString.copyFromUtf8(URL))
				.addAllFrozenSupply(frozenList)
				.build());
		AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Frozen supply cannot exceed total supply");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Frozen supply cannot exceed total supply", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * SameTokenName close, Invalid ownerAddress
	 */
	@Test
	public void SameTokenNameCloseInvalidOwnerAddress() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		long nowTime = new Date().getTime();
		Any any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString("12312315345345")))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid ownerAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid ownerAddress", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * SameTokenName open, check invalid precision
	 */
	@Test
	public void SameTokenNameCloseInvalidPrecision() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
		long nowTime = new Date().getTime();
		Any any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(9)
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		byte[] stats = new byte[27];
		Arrays.fill(stats, (byte) 1);
		dbManager.getDynamicPropertiesStore()
				.statsByVersion(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT, stats);
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Precision cannot exceed 8");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Precision cannot exceed 8", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * SameTokenName close, Invalid abbreviation for token
	 */
	@Test
	public void SameTokenNameCloseInvalidAddr() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		long nowTime = new Date().getTime();
		Any any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setAbbr(ByteString.copyFrom(ByteArray.fromHexString(
								"a0299f3db80a24123b20a254b89ce639d59132f157f13")))
						.setPrecision(4)
						.build());

		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		byte[] stats = new byte[27];
		Arrays.fill(stats, (byte) 1);
		dbManager.getDynamicPropertiesStore()
				.statsByVersion(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT, stats);

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid abbreviation for token");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid abbreviation for token", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * repeat issue assert name,
	 */
	@Test
	public void IssueSameTokenNameAssert() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		String ownerAddress = "a08beaa1a8e2d45367af7bae7c49009876a4fa4301";

		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		Contract.AssetIssueContract assetIssueContract =
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
						.setName(ByteString.copyFrom(ByteArray.fromString(NAME)))
						.setId(Long.toString(id))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(1)
						.setEndTime(100)
						.setVoteScore(2)
						.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
						.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
						.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		AccountCapsule ownerCapsule =
				new AccountCapsule(
						ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)),
						ByteString.copyFromUtf8("owner11"),
						AccountType.AssetIssue);
		ownerCapsule.addAsset(NAME.getBytes(), 1000L);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		// SameTokenName not active, same assert name, should failure
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Token exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Token exists", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// SameTokenName active, same assert name,should success
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 =
					dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum)));
			Assert.assertNotNull(assetIssueCapsuleV2);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
					blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
					TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}

	/**
	 * SameTokenName close, check invalid param "PublicFreeAssetNetUsage must be 0!" "Invalid
	 * FreeAssetNetLimit" "Invalid PublicFreeAssetNetLimit" "Account not exists" "No enough balance
	 * for fee!"
	 */
	@Test
	public void SameTokenNameCloseInvalidparam() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		long nowTime = new Date().getTime();
		byte[] stats = new byte[27];
		Arrays.fill(stats, (byte) 1);
		dbManager.getDynamicPropertiesStore()
				.statsByVersion(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT, stats);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		// PublicFreeAssetNetUsage must be 0!
		Any any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(3)
						.setPublicFreeAssetNetUsage(100)
						.build());
		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("PublicFreeAssetNetUsage must be 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("PublicFreeAssetNetUsage must be 0!", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//Invalid FreeAssetNetLimit
		any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(3)
						.setFreeAssetNetLimit(-10)
						.build());
		actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid FreeAssetNetLimit");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid FreeAssetNetLimit", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//Invalid PublicFreeAssetNetLimit
		any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(3)
						.setPublicFreeAssetNetLimit(-10)
						.build());
		actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid PublicFreeAssetNetLimit");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid PublicFreeAssetNetLimit", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}
	}


	/**
	 * SameTokenName close, account not good "Account not exists" "No enough balance for fee!"
	 */
	@Test
	public void SameTokenNameCloseInvalidAccount() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
		long nowTime = new Date().getTime();
		byte[] stats = new byte[27];
		Arrays.fill(stats, (byte) 1);
		dbManager.getDynamicPropertiesStore()
				.statsByVersion(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT, stats);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		// No enough balance for fee!
		Any any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(3)
						.build());
		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setBalance(1000);
		dbManager.getAccountStore().put(owner.createDbKey(), owner);

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No enough balance for fee!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("No enough balance for fee!", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

		//Account not exists
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
		any = Any.pack(
				Contract.AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFromUtf8(NAME))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(nowTime)
						.setEndTime(nowTime + 24 * 3600 * 1000)
						.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
						.setUrl(ByteString.copyFromUtf8(URL))
						.setPrecision(3)
						.build());
		actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account not exists", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
		}

	}

}