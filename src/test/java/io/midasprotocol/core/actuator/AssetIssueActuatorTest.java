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
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract.FrozenSupply;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class AssetIssueActuatorTest {

	private static final String dbPath = "output_asset_issue_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_SECOND;
	private static final String NAME = "my-token";
	private static final long TOTAL_SUPPLY = 10000L;
	private static final int TRX_NUM = 10000;
	private static final int NUM = 100000;
	private static final String DESCRIPTION = "myCoin";
	private static final String URL = "my-token.com";
	private static final String ASSET_NAME_SECOND = "asset_name2";
	private static ApplicationContext context;
	private static Manager dbManager;
	private static long now = 0;
	private static long startTime = 0;
	private static long endTime = 0;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
		OWNER_ADDRESS_SECOND = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
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
				.setMcashNum(TRX_NUM)
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
		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueStore().get(tokenIdNum);
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getMcashNum());
			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}


	/**
	 * Init close SameTokenName,after init data,open SameTokenName
	 */
	@Test
	public void oldNotUpdateAssetIssueSuccess() {
		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueStore().get(tokenIdNum);
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getMcashNum());
			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), TOTAL_SUPPLY);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}

	/**
	 * SameTokenName open, asset issue success
	 */
	@Test
	public void SameTokenNameOpenAssetIssueSuccess() {
		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueStore().get(tokenIdNum);
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
			Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
			Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getMcashNum());
			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(-TRX_NUM)
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
			Assert.assertEquals("McashNum must greater than 0!", e.getMessage());
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
				dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(0)
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
			Assert.assertEquals("McashNum must greater than 0!", e.getMessage());
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
				dbManager.getAssetIssueStore().get(ByteArray.fromString(NAME));
			Assert.assertEquals(owner.getBalance(),
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance);
			Assert.assertNull(assetIssueCapsule);
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		// Too long name, throw exception. Max long is 32.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklmo"))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		// Contain space, throw exception. Every character need readable.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8("t e"))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		// Contain chinese character, throw exception.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95")))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		// 32 byte readable character just ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklm"))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		createCapsule();
		// 1 byte readable character ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8("0"))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" +
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789" +
			"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		//Too long url, throw exception. Max long is 256.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(0, owner.getInstance().getAssetsMap().size());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		// 256 byte readable character just ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);
			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		createCapsule();
		// 1 byte url.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
				dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		createCapsule();
		// 1 byte space ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule =
				dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		// 200 bytes character just ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		createCapsule();
		// Empty description is ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		createCapsule();
		// 1 byte space ok.
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(assetIssueId);
			Assert.assertNotNull(assetIssueCapsule);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(), TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//frozen_amount < 0 throw exception.
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(-1)
			.build();
		contract = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//FrozenDays < 0 throw exception.
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(-1).setFrozenAmount(1)
			.build();
		contract = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
				.setMcashNum(TRX_NUM)
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
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//frozen_amount = 1 and  frozenDays = 1 is OK
		frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(1)
			.build();
		contract = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//empty end time will throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//startTime == now, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//startTime < now, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//endTime == startTime, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//endTime < startTime, throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//right issue, will not throw exception
		contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			Assert.assertEquals(account.getAssetIssuedId(), assetIssueId);
			Assert.assertEquals(account.getAssetMap().size(), 1);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}

	@Test
	public void assetIssueMcashNameTest() {
		Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8("MCASH"))
			.setTotalSupply(TOTAL_SUPPLY)
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
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
			.setMcashNum(TRX_NUM).setNum(NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}

	/**
	 * SameTokenName close, Invalid ownerAddress
	 */
	@Test
	public void SameTokenNameCloseInvalidOwnerAddress() {
		long nowTime = new Date().getTime();
		Any any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString("12312315345345")))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}

	/**
	 * SameTokenName open, check invalid precision
	 */
	@Test
	public void SameTokenNameCloseInvalidPrecision() {
		long nowTime = new Date().getTime();
		Any any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.setPrecision(9)
				.build());

		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Precision cannot exceed 8");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Precision cannot exceed 8", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}

	/**
	 * SameTokenName close, Invalid abbreviation for token
	 */
	@Test
	public void SameTokenNameCloseInvalidAddr() {
		long nowTime = new Date().getTime();
		Any any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid abbreviation for token");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid abbreviation for token", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}

	/**
	 * repeat issue assert name,
	 */
	@Test
	public void IssueSameTokenNameAssert() {
		String ownerAddress = "a08beaa1a8e2d45367af7bae7c49009876a4fa4301";

		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		Contract.AssetIssueContract assetIssueContract =
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setName(ByteString.copyFrom(ByteArray.fromString(NAME)))
				.setId(id)
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
		ownerCapsule.addAsset(id, 1000L);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertNotEquals(0, ret.getInstance().getAssetIssueId());
			long assetIssueId = ret.getInstance().getAssetIssueId();
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsuleV2 =
				dbManager.getAssetIssueStore().get(tokenIdNum);
			Assert.assertNotNull(assetIssueCapsuleV2);

			Assert.assertEquals(owner.getBalance(), 0L);
			Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
				blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
			Assert.assertEquals(owner.getAssetMap().get(assetIssueId).longValue(),
				TOTAL_SUPPLY);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(id);
			}
		}
	}

	/**
	 * SameTokenName close, check invalid param "PublicFreeAssetBandwidthUsage must be 0!" "Invalid
	 * FreeAssetBandwidthLimit" "Invalid PublicFreeAssetBandwidthLimit" "Account not exists" "No enough balance
	 * for fee!"
	 */
	@Test
	public void SameTokenNameCloseInvalidparam() {
		long nowTime = new Date().getTime();
		TransactionResultCapsule ret = new TransactionResultCapsule();

		// PublicFreeAssetBandwidthUsage must be 0!
		Any any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.setPrecision(3)
				.setPublicFreeAssetBandwidthUsage(100)
				.build());
		AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("PublicFreeAssetBandwidthUsage must be 0!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("PublicFreeAssetBandwidthUsage must be 0!", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//Invalid FreeAssetBandwidthLimit
		any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.setPrecision(3)
				.setFreeAssetBandwidthLimit(-10)
				.build());
		actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid FreeAssetBandwidthLimit");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid FreeAssetBandwidthLimit", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//Invalid PublicFreeAssetBandwidthLimit
		any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(nowTime)
				.setEndTime(nowTime + Parameter.TimeConstant.MS_PER_DAY)
				.setDescription(ByteString.copyFromUtf8(DESCRIPTION))
				.setUrl(ByteString.copyFromUtf8(URL))
				.setPrecision(3)
				.setPublicFreeAssetBandwidthLimit(-10)
				.build());
		actuator = new AssetIssueActuator(any, dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid PublicFreeAssetBandwidthLimit");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid PublicFreeAssetBandwidthLimit", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}
	}


	/**
	 * SameTokenName close, account not good "Account not exists" "No enough balance for fee!"
	 */
	@Test
	public void SameTokenNameCloseInvalidAccount() {
		long nowTime = new Date().getTime();
		TransactionResultCapsule ret = new TransactionResultCapsule();

		// No enough balance for fee!
		Any any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

		//Account not exists
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
		any = Any.pack(
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFromUtf8(NAME))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
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
			long tokenIdNumber = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(tokenIdNumber);
			if (assetIssueCapsule != null && assetIssueCapsule.getName()
				.equals(ByteString.copyFrom(ByteArray.fromString(NAME)))) {
				dbManager.getAssetIssueStore().delete(tokenIdNumber);
			}
		}

	}

}