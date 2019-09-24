package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter.ChainConstant;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

import static junit.framework.TestCase.fail;

@Slf4j
public class TransferActuatorTest {

	private static final String dbPath = "output_transfer_test";
	private static final String OWNER_ADDRESS;
	private static final String TO_ADDRESS;
	private static final long AMOUNT = 100;
	private static final long OWNER_BALANCE = 999999999;
	private static final long TO_BALANCE = 100001;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String TO_ADDRESS_INVALID = "bbb";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final String OWNER_NO_BALANCE;
	private static final String TO_ACCOUNT_INVALID;
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		TO_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		OWNER_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
		OWNER_NO_BALANCE = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3433";
		TO_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3422";
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
				OWNER_BALANCE);
		AccountCapsule toAccountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("toAccount"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
				AccountType.Normal,
				TO_BALANCE);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	private Any getContract(long count) {
		return Any.pack(
			Contract.TransferContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAmount(count)
				.build());
	}

	private Any getContract(long count, String owneraddress, String toaddress) {
		return Any.pack(
			Contract.TransferContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owneraddress)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(toaddress)))
				.setAmount(count)
				.build());
	}

	private Any getContract(long amount, String memo) {
		ByteString memoBa = StringUtils.isBlank(memo) ? ByteString.EMPTY : ByteString.copyFrom(ByteArray.fromString(memo));
		return Any.pack(
			Contract.TransferContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAmount(amount)
				.setMemo(memoBa)
				.build()
		);
	}

	@Test
	public void rightTransfer() {
		TransferActuator actuator = new TransferActuator(getContract(AMOUNT), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE - AMOUNT - ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE + AMOUNT);
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void perfectTransfer() {
		TransferActuator actuator = new TransferActuator(
			getContract(OWNER_BALANCE - ChainConstant.TRANSFER_FEE), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), 0);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE + OWNER_BALANCE);
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void moreTransfer() {
		TransferActuator actuator = new TransferActuator(getContract(OWNER_BALANCE + 1), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("balance is not sufficient");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Validate TransferContract error, balance is not sufficient.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}


	@Test
	public void invalidOwnerAddress() {
		TransferActuator actuator = new TransferActuator(
			getContract(10000L, OWNER_ADDRESS_INVALID, TO_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid ownerAddress");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid ownerAddress", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);

		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	@Test
	public void invalidToAddress() {
		TransferActuator actuator = new TransferActuator(
			getContract(10000L, OWNER_ADDRESS, TO_ADDRESS_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid toAddress");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid toAddress", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	@Test
	public void selfTransfer() {
		TransferActuator actuator = new TransferActuator(
			getContract(100L, OWNER_ADDRESS, OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot transfer mcash to yourself.");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Cannot transfer mcash to yourself.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notExitOwnerAccount() {
		TransferActuator actuator = new TransferActuator(
			getContract(100L, OWNER_ACCOUNT_INVALID, TO_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Validate TransferContract error, no OwnerAccount.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Validate TransferContract error, no OwnerAccount.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notExistToAccount() {
		TransferActuator actuator = new TransferActuator(
			getContract(1_000_000L, OWNER_ADDRESS, TO_ACCOUNT_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			AccountCapsule noExitAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ACCOUNT_INVALID));
			Assert.assertNull(noExitAccount);
			actuator.validate();
			actuator.execute(ret);
			noExitAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ACCOUNT_INVALID));
			Assert.assertNotNull(noExitAccount);
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE - 1_000_000L);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
			noExitAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ACCOUNT_INVALID));
			Assert.assertEquals(noExitAccount.getBalance(), 1_000_000L);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAccountStore().delete(ByteArray.fromHexString(TO_ACCOUNT_INVALID));
		}
	}

	@Test
	public void zeroAmountTest() {
		TransferActuator actuator = new TransferActuator(getContract(0), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Amount must greater than 0.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Amount must greater than 0.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void negativeAmountTest() {
		TransferActuator actuator = new TransferActuator(getContract(-AMOUNT), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Amount must greater than 0.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Amount must greater than 0.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void addOverflowTest() {
		// First, increase the to balance. Else can't complete this test case.
		AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
		toAccount.setBalance(Long.MAX_VALUE);
		dbManager.getAccountStore().put(ByteArray.fromHexString(TO_ADDRESS), toAccount);
		TransferActuator actuator = new TransferActuator(getContract(1), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("long overflow");
		} catch (ContractValidateException e) {
			Assert.assertEquals(("long overflow"), e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), Long.MAX_VALUE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void insufficientFee() {
		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_NO_BALANCE)),
				AccountType.Normal,
				-10000L);
		AccountCapsule toAccountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("toAccount"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ACCOUNT_INVALID)),
				AccountType.Normal,
				100L);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);

		TransferActuator actuator = new TransferActuator(
			getContract(AMOUNT, OWNER_NO_BALANCE, TO_ACCOUNT_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Validate TransferContract error, insufficient fee.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Validate TransferContract error, balance is not sufficient.",
				e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAccountStore().delete(ByteArray.fromHexString(TO_ACCOUNT_INVALID));
		}
	}

	@Test
	public void memo() {
		TransferActuator actuator = new TransferActuator(getContract(AMOUNT, "Hello from MCash"), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE - AMOUNT - ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE + AMOUNT);
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void emptyMemo() {
		TransferActuator actuator = new TransferActuator(getContract(AMOUNT, ""), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE - AMOUNT - ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE + AMOUNT);
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void memoTooLong() {
		TransferActuator actuator = new TransferActuator(getContract(AMOUNT,
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid memo length");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid memo length", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertEquals(owner.getBalance(), OWNER_BALANCE);
			Assert.assertEquals(toAccount.getBalance(), TO_BALANCE);

		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}
