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
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class SetAccountIdActuatorTest {

	private static final String dbPath = "output_set_account_id_test";
	private static final String ACCOUNT_NAME = "ownertest";
	private static final String ACCOUNT_NAME_1 = "ownertest1";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_1;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		OWNER_ADDRESS_1 = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
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
	public void createCapsule() {
		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				ByteString.EMPTY,
				AccountType.Normal);
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_1));
		dbManager.getAccountIdIndexStore().delete(ACCOUNT_NAME.getBytes());
		dbManager.getAccountIdIndexStore().delete(ACCOUNT_NAME_1.getBytes());
	}

	private Any getContract(String name, String address) {
		return Any.pack(
			Contract.SetAccountIdContract.newBuilder()
				.setAccountId(ByteString.copyFromUtf8(name))
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.build());
	}

	/**
	 * Unit test.
	 */

	private Any getContract(ByteString name, String address) {
		return Any.pack(
			Contract.SetAccountIdContract.newBuilder()
				.setAccountId(name)
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.build());
	}

	/**
	 * set account id when all right.
	 */
	@Test
	public void rightSetAccountId() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		SetAccountIdActuator actuator = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountId().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException e) {
			logger.info(e.getMessage());
			Assert.fail(e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidAddress() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		SetAccountIdActuator actuator = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS_INVALID), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid ownerAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid ownerAddress", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noneExistAccount() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		SetAccountIdActuator actuator = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS_1), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account has not existed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account has not existed", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void twiceUpdateAccount() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		SetAccountIdActuator actuator = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS), dbManager);
		SetAccountIdActuator actuator1 = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME_1, OWNER_ADDRESS), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountId().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		try {
			actuator1.validate();
			actuator1.execute(ret);
			Assert.fail("This account id already set");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This account id already set", e.getMessage());
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountId().toStringUtf8());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void nameAlreadyUsed() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		SetAccountIdActuator actuator = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS), dbManager);
		SetAccountIdActuator actuator1 = new SetAccountIdActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS_1), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountId().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException e) {
			logger.info(e.getMessage());
			Assert.fail(e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_1)),
				ByteString.EMPTY,
				AccountType.Normal);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		try {
			actuator1.validate();
			actuator1.execute(ret);
			Assert.fail("Id has existed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This id has existed", e.getMessage());
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountId().toStringUtf8());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	/*
	 * Account name need 8 - 32 bytes.
	 */
	public void invalidName() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		//Just OK 32 bytes is OK
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract("testname0123456789abcdefghijgklm", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals("testname0123456789abcdefghijgklm",
				accountCapsule.getAccountId().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//8 bytes is OK
		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountId(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract("test1111", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals("test1111",
				accountCapsule.getAccountId().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException e) {
			logger.info(e.getMessage());
			Assert.fail(e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//Empty name
		accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountId(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract(ByteString.EMPTY, OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountId", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//Too long name 33 bytes
		accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountId(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract("testname0123456789abcdefghijgklmo0123456789abcdefghijgk"
					+ "lmo0123456789abcdefghijgklmo0123456789abcdefghijgklmo0123456789abcdefghijgklmo"
					+ "0123456789abcdefghijgklmo0123456789abcdefghijgklmo0123456789abcdefghijgklmo"
					+ "0123456789abcdefghijgklmo0123456789abcdefghijgklmo", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid accountId");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountId", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//Too short name 7 bytes
		accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountId(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract("testnam", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid accountId");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountId", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// Can't contain space
		accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountId(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract("t e", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid accountId");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountId", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// Can't contain chinese characters
		accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountId(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			SetAccountIdActuator actuator = new SetAccountIdActuator(
				getContract(ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95"))
					, OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid accountId");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountId", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}
