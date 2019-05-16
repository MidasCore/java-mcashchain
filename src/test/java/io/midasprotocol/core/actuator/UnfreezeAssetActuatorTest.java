package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
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
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.Account.Frozen;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;

import java.io.File;

@Slf4j
public class UnfreezeAssetActuatorTest {

	private static final String dbPath = "output_unfreeze_asset_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(10_000);
	private static final long frozenBalance = ConversionUtil.McashToMatoshi(1_000);
	private static final String assetName = "testCoin";
	private static final String assetID = "123456";
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		OWNER_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
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
	public void createAccountCapsule() {
//    AccountCapsule ownerCapsule =
//        new AccountCapsule(
//            ByteString.copyFromUtf8("owner"),
//            StringUtil.hexString2ByteString(OWNER_ADDRESS),
//            AccountType.Normal,
//            initBalance);
//    ownerCapsule.setAssetIssuedName(assetName.getBytes());
//    dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
	}

	@Before
	public void createAsset() {
//    AssetIssueContract.Builder builder = AssetIssueContract.newBuilder();
//    builder.setName(ByteString.copyFromUtf8(assetName));
//    builder.setId(assetID);
//    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(builder.build());
//    dbManager.getAssetIssueStore().put(assetName.getBytes(),assetIssueCapsule);
	}

	private Any getContract(String ownerAddress) {
		return Any.pack(
				Contract.UnfreezeAssetContract.newBuilder()
						.setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
						.build());
	}


	private void createAssertBeforSameTokenNameActive() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);

		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		AssetIssueContract.Builder builder = AssetIssueContract.newBuilder();
		builder.setName(ByteString.copyFromUtf8(assetName));
		builder.setId(String.valueOf(tokenId));
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(builder.build());
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);

		AccountCapsule ownerCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("owner"),
						StringUtil.hexString2ByteString(OWNER_ADDRESS),
						AccountType.Normal,
						initBalance);
		ownerCapsule.setAssetIssuedName(assetName.getBytes());
		ownerCapsule.setAssetIssuedID(assetIssueCapsule.createDbV2Key());
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
	}

	private void createAssertSameTokenNameActive() {
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);

		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		AssetIssueContract.Builder builder = AssetIssueContract.newBuilder();
		builder.setName(ByteString.copyFromUtf8(assetName));
		builder.setId(String.valueOf(tokenId));
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(builder.build());
		dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);

		AccountCapsule ownerCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("owner"),
						StringUtil.hexString2ByteString(OWNER_ADDRESS),
						AccountType.Normal,
						initBalance);
		ownerCapsule.setAssetIssuedName(assetName.getBytes());
		ownerCapsule.setAssetIssuedID(assetIssueCapsule.createDbV2Key());
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
	}

	/**
	 * SameTokenName close, Unfreeze assert success.
	 */
	@Test
	public void SameTokenNameCloseUnfreezeAsset() {
		createAssertBeforSameTokenNameActive();
		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		Account account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
				.getInstance();
		Frozen newFrozen0 = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance)
				.setExpireTime(now)
				.build();
		Frozen newFrozen1 = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance + 1)
				.setExpireTime(now + 600000)
				.build();
		account = account.toBuilder().addFrozenSupply(newFrozen0).addFrozenSupply(newFrozen1).build();
		AccountCapsule accountCapsule = new AccountCapsule(account);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			// V1
			Assert.assertEquals(owner.getAssetMap().get(assetName).longValue(), frozenBalance);
			// V2
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenId)).longValue(),
					frozenBalance);
			Assert.assertEquals(owner.getFrozenSupplyCount(), 1);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * SameTokenName active, Unfreeze assert success.
	 */
	@Test
	public void SameTokenNameActiveUnfreezeAsset() {
		createAssertSameTokenNameActive();
		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		Account account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
				.getInstance();
		Frozen newFrozen0 = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance)
				.setExpireTime(now)
				.build();
		Frozen newFrozen1 = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance + 1)
				.setExpireTime(now + 600000)
				.build();
		account = account.toBuilder().addFrozenSupply(newFrozen0).addFrozenSupply(newFrozen1).build();
		AccountCapsule accountCapsule = new AccountCapsule(account);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			// V1 assert not exist
			Assert.assertNull(owner.getAssetMap().get(assetName));
			// V2
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenId)).longValue(),
					frozenBalance);
			Assert.assertEquals(owner.getFrozenSupplyCount(), 1);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}


	/**
	 * when init data, SameTokenName is close, then open SameTokenName, Unfreeze assert success.
	 */
	@Test
	public void SameTokenNameActiveInitAndAcitveUnfreezeAsset() {
		createAssertBeforSameTokenNameActive();
		dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
		long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		Account account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
				.getInstance();
		Frozen newFrozen0 = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance)
				.setExpireTime(now)
				.build();
		Frozen newFrozen1 = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance + 1)
				.setExpireTime(now + 600000)
				.build();
		account = account.toBuilder().addFrozenSupply(newFrozen0).addFrozenSupply(newFrozen1).build();
		AccountCapsule accountCapsule = new AccountCapsule(account);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			// V1 assert not exist
			Assert.assertNull(owner.getAssetMap().get(assetName));
			// V2
			Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenId)).longValue(),
					frozenBalance);
			Assert.assertEquals(owner.getFrozenSupplyCount(), 1);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		createAssertBeforSameTokenNameActive();
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS_INVALID),
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
		}
	}

	@Test
	public void invalidOwnerAccount() {
		createAssertBeforSameTokenNameActive();
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ACCOUNT_INVALID),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + OWNER_ACCOUNT_INVALID + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ACCOUNT_INVALID + " does not exist",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notIssueAsset() {
		createAssertBeforSameTokenNameActive();
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		Account account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
				.getInstance();
		Frozen newFrozen = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance)
				.setExpireTime(now)
				.build();
		account = account.toBuilder().addFrozenSupply(newFrozen).setAssetIssuedName(ByteString.EMPTY)
				.build();
		AccountCapsule accountCapsule = new AccountCapsule(account);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("This account did not issue any asset");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This account did not issue any asset", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noFrozenSupply() {
		createAssertBeforSameTokenNameActive();
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No frozen supply balance");
		} catch (ContractValidateException e) {
			Assert.assertEquals("No frozen supply balance", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notTimeToUnfreeze() {
		createAssertBeforSameTokenNameActive();
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		Account account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
				.getInstance();
		Frozen newFrozen = Frozen.newBuilder()
				.setFrozenBalance(frozenBalance)
				.setExpireTime(now + 60000)
				.build();
		account = account.toBuilder().addFrozenSupply(newFrozen).build();
		AccountCapsule accountCapsule = new AccountCapsule(account);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeAssetActuator actuator = new UnfreezeAssetActuator(getContract(OWNER_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("It's not time to unfreeze asset supply");
		} catch (ContractValidateException e) {
			Assert.assertEquals("It's not time to unfreeze asset supply", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}