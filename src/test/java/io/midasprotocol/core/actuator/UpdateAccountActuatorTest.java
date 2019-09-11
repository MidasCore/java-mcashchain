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
public class UpdateAccountActuatorTest {

	private static final String dbPath = "output_update_account_test";
	private static final String ACCOUNT_NAME = "ownerTest";
	private static final String ACCOUNT_NAME_1 = "ownerTest1";
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
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_1));
		dbManager.getAccountIdIndexStore().delete(ACCOUNT_NAME.getBytes());
		dbManager.getAccountIdIndexStore().delete(ACCOUNT_NAME_1.getBytes());
	}

	private Any getContract(String name, String address) {
		return Any.pack(
			Contract.AccountUpdateContract.newBuilder()
				.setAccountName(ByteString.copyFromUtf8(name))
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.build());
	}

	private Any getContract(ByteString name, String address) {
		return Any.pack(
			Contract.AccountUpdateContract.newBuilder()
				.setAccountName(name)
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.build());
	}

	/**
	 * Update account when all right.
	 */
	@Test
	public void rightUpdateAccount() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UpdateAccountActuator actuator = new UpdateAccountActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountName().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidAddress() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UpdateAccountActuator actuator = new UpdateAccountActuator(
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
	public void noExitAccount() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UpdateAccountActuator actuator = new UpdateAccountActuator(
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

	//@Test
	/*
	 * Can update name only one time.
	 */
	public void twiceUpdateAccount() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UpdateAccountActuator actuator = new UpdateAccountActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS), dbManager);
		UpdateAccountActuator actuator1 = new UpdateAccountActuator(
			getContract(ACCOUNT_NAME_1, OWNER_ADDRESS), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountName().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		try {
			actuator1.validate();
			actuator1.execute(ret);
			Assert.fail("This account name already exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This account name already exist", e.getMessage());
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountName().toStringUtf8());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	//@Test
	public void nameAlreadyUsed() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UpdateAccountActuator actuator = new UpdateAccountActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS), dbManager);
		UpdateAccountActuator actuator1 = new UpdateAccountActuator(
			getContract(ACCOUNT_NAME, OWNER_ADDRESS_1), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountName().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
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
			Assert.fail("This name has existed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This name has existed", e.getMessage());
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ACCOUNT_NAME, accountCapsule.getAccountName().toStringUtf8());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	/*
	 * Account name need 8 - 32 bytes.
	 */
	public void invalidName() {
		dbManager.getDynamicPropertiesStore().saveAllowUpdateAccountName(1);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		//Just OK 32 bytes is OK
		try {
			UpdateAccountActuator actuator = new UpdateAccountActuator(
				getContract("testname0123456789abcdefghijgklm", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals("testname0123456789abcdefghijgklm",
				accountCapsule.getAccountName().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
		//8 bytes is OK
		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setAccountName(ByteString.EMPTY.toByteArray());
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			UpdateAccountActuator actuator = new UpdateAccountActuator(
				getContract("testname", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals("testname",
				accountCapsule.getAccountName().toStringUtf8());
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
		//Empty name
		try {
			UpdateAccountActuator actuator = new UpdateAccountActuator(
				getContract(ByteString.EMPTY, OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountName", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
		//Too long name 33 bytes
		try {
			UpdateAccountActuator actuator = new UpdateAccountActuator(
				getContract("testname0123456789abcdefghijgklmo0123456789abcdefghijgk"
					+ "lmo0123456789abcdefghijgklmo0123456789abcdefghijgklmo0123456789abcdefghijgklmo"
					+ "0123456789abcdefghijgklmo0123456789abcdefghijgklmo0123456789abcdefghijgklmo"
					+ "0123456789abcdefghijgklmo0123456789abcdefghijgklmo", OWNER_ADDRESS), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid accountName");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid accountName", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
//		//Too short name 7 bytes
//		try {
//			UpdateAccountActuator actuator = new UpdateAccountActuator(
//					getContract("testnam", OWNER_ADDRESS), dbManager);
//			actuator.validate();
//			actuator.execute(ret);
//			Assert.assertFalse(true);
//		} catch (ContractValidateException e) {
//			Assert.assertTrue(e instanceof ContractValidateException);
//			Assert.assertEquals("Invalid accountName", e.getMessage());
//		} catch (ContractExeException e) {
//			Assert.assertFalse(e instanceof ContractExeException);
//		}
//
//		//Can't contain space
//		try {
//			UpdateAccountActuator actuator = new UpdateAccountActuator(
//					getContract("t e", OWNER_ADDRESS), dbManager);
//			actuator.validate();
//			actuator.execute(ret);
//			Assert.assertFalse(true);
//		} catch (ContractValidateException e) {
//			Assert.assertTrue(e instanceof ContractValidateException);
//			Assert.assertEquals("Invalid accountName", e.getMessage());
//		} catch (ContractExeException e) {
//			Assert.assertFalse(e instanceof ContractExeException);
//		}
//		//Can't contain chinese characters
//		try {
//			UpdateAccountActuator actuator = new UpdateAccountActuator(
//					getContract(ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95"))
//							, OWNER_ADDRESS), dbManager);
//			actuator.validate();
//			actuator.execute(ret);
//			Assert.assertFalse(true);
//		} catch (ContractValidateException e) {
//			Assert.assertTrue(e instanceof ContractValidateException);
//			Assert.assertEquals("Invalid accountName", e.getMessage());
//		} catch (ContractExeException e) {
//			Assert.assertFalse(e instanceof ContractExeException);
//		}
	}
}
