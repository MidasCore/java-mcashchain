package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;

import java.io.File;
import java.util.Date;

@Slf4j
public class UpdateAssetActuatorTest {

	private static final String dbPath = "output_update_asset_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
	private static final String SECOND_ACCOUNT_ADDRESS;
	private static final String OWNER_ADDRESS_NOTEXIST;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String NAME = "my-asset";
	private static final long TOTAL_SUPPLY = 10000L;
	private static final String DESCRIPTION = "myCoin";
	private static final String URL = "my-asset.com";
	private static ApplicationContext context;
	private static Application AppT;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		AppT = ApplicationFactory.create(context);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		OWNER_ADDRESS_NOTEXIST = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		SECOND_ACCOUNT_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
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
		AppT.shutdownServices();
		AppT.shutdown();
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
		// address in accountStore not the owner of contract
		AccountCapsule secondAccount =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS)),
				ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS), secondAccount);

		// address does not exist in accountStore
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOTEXIST));
	}

	private Any getContract(String accountAddress, String description, String url, long newLimit, long newPublicLimit) {
		return Any.pack(
			Contract.UpdateAssetContract.newBuilder()
				.setOwnerAddress(StringUtil.hexString2ByteString(accountAddress))
				.setDescription(ByteString.copyFromUtf8(description))
				.setUrl(ByteString.copyFromUtf8(url))
				.setNewLimit(newLimit)
				.setNewPublicLimit(newPublicLimit)
				.build());
	}

	private Contract.AssetIssueContract getAssetIssueContract() {
		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(tokenId);

		long nowTime = new Date().getTime();
		return Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setName(ByteString.copyFromUtf8(NAME))
			.setTotalSupply(TOTAL_SUPPLY)
			.setId(tokenId)
			.setMcashNum(100)
			.setNum(10)
			.setStartTime(nowTime)
			.setEndTime(nowTime + 24 * 3600 * 1000)
			.setOrder(0)
			.setDescription(ByteString.copyFromUtf8("assetTest"))
			.setUrl(ByteString.copyFromUtf8("tron.test.com"))
			.build();
	}

	private void createAsset() {
		// address in accountStore and the owner of contract
		AccountCapsule accountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);

		// add asset issue
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(getAssetIssueContract());
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		accountCapsule.setAssetIssuedId(assetIssueCapsule.getId());
		accountCapsule.addAsset(assetIssueCapsule.getId(), TOTAL_SUPPLY);

		dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule);
	}

	@Test
	public void successUpdateAssetAfter() {
		createAsset();
		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UpdateAssetActuator actuator;
		actuator = new UpdateAssetActuator(getContract(OWNER_ADDRESS, DESCRIPTION, URL,
			500L, 8000L), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			AssetIssueCapsule assetIssueCapsuleV2 =
				dbManager.getAssetIssueStore().get(tokenId);
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(
				DESCRIPTION, assetIssueCapsuleV2.getInstance().getDescription().toStringUtf8());
			Assert.assertEquals(URL, assetIssueCapsuleV2.getInstance().getUrl().toStringUtf8());
			Assert.assertEquals(assetIssueCapsuleV2.getFreeAssetBandwidthLimit(), 500L);
			Assert.assertEquals(assetIssueCapsuleV2.getPublicFreeAssetBandwidthLimit(), 8000L);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	@Test
	public void invalidAddress() {
		createAsset();
		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(OWNER_ADDRESS_INVALID, DESCRIPTION, URL, 500L, 8000L),
				dbManager);

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
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	@Test
	public void noExistAccount() {
		createAsset();
		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(OWNER_ADDRESS_NOTEXIST, DESCRIPTION, URL, 500L, 8000L),
				dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Account has not existed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account has not existed", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	@Test
	public void noAsset() {
		createAsset();
		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(SECOND_ACCOUNT_ADDRESS, DESCRIPTION, URL, 500L, 8000L),
				dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Account has not issue any asset");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account has not issue any asset", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	/*
	 * empty url
	 */
	@Test
	public void invalidAssetUrl() {
		createAsset();
		String localUrl = "";
		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(OWNER_ADDRESS, DESCRIPTION, localUrl, 500L, 8000L),
				dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	/*
	 * description is more than 200 character
	 */
	@Test
	public void invalidAssetDescription() {
		createAsset();
		String localDescription =
			"abchefghijklmnopqrstuvwxyzabchefghijklmnopqrstuvwxyzabchefghijklmnopqrstuv"
				+ "wxyzabchefghijklmnopqrstuvwxyzabchefghijklmnopqrstuvwxyzabchefghijklmnopqrstuvwxyzabchefghij"
				+ "klmnopqrstuvwxyzabchefghijklmnopqrstuvwxyzabchefghijklmnopqrstuvwxyz";

		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(OWNER_ADDRESS, localDescription, URL, 500L, 8000L),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Invalid description");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid description", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	/*
	 * new limit is more than 57_600_000_000
	 */
	@Test
	public void invalidNewLimit() {
		createAsset();
		long localNewLimit = 57_600_000_001L;
		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(OWNER_ADDRESS, DESCRIPTION, URL, localNewLimit, 8000L), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Invalid FreeAssetNetLimit");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid FreeAssetNetLimit", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}

	@Test
	public void invalidNewPublicLimit() {
		createAsset();
		long localNewPublicLimit = -1L;
		UpdateAssetActuator actuator =
			new UpdateAssetActuator(
				getContract(OWNER_ADDRESS, DESCRIPTION, URL, 500L, localNewPublicLimit), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Invalid PublicFreeAssetNetLimit");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid PublicFreeAssetNetLimit", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAssetIssueStore().delete(ByteString.copyFromUtf8(NAME).toByteArray());
		}
	}
}
