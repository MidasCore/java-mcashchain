package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.StringUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.config.args.Witness;
import org.tron.core.db.Manager;
import org.tron.core.exception.BalanceInsufficientException;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;

@Slf4j
public class WithdrawBalanceActuatorTest {

	private static final String dbPath = "output_withdraw_balance_test";
	private static final String SUPERNODE_ADDRESS;
	private static final String SUPERNODE_ADDRESS_INVALID = "aaaa";
	private static final String SUPERNODE_ACCOUNT_INVALID;
	private static final String OWNER_ADDRESS;
	private static final long initBalance = 10_000_000_000L;
	private static final long allowance = 32_000_000L;
	private static Manager dbManager;
	private static TronApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS = "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ACCOUNT_INVALID = "548794500882809695a8a687866e76d4271a3456";
		OWNER_ADDRESS = "4536f33f3e0a725484738017c533f877d5df3a82";
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
	public void createAccountCapsule() {
		AccountCapsule ownerCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("owner"),
						ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS)),
						AccountType.Normal,
						initBalance);
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
	}

	private Any getContract(String ownerAddress) {
		return Any.pack(
				Contract.WithdrawBalanceContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
						.build());
	}

	@Test
	public void testWithdrawBalance() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
		byte[] address = ByteArray.fromHexString(SUPERNODE_ADDRESS);
		try {
			dbManager.adjustAllowance(address, allowance);
		} catch (BalanceInsufficientException e) {
			Assert.fail("BalanceInsufficientException");
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		Assert.assertEquals(accountCapsule.getAllowance(), allowance);
		Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), 0);

		WitnessCapsule witnessCapsule = new WitnessCapsule(
				ByteString.copyFrom(address),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				100, "http://baidu.com");
		dbManager.getWitnessStore().put(address, witnessCapsule);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(SUPERNODE_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(SUPERNODE_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance + allowance);
			Assert.assertEquals(owner.getAllowance(), 0);
			Assert.assertNotEquals(owner.getLatestWithdrawTime(), 0);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}


	@Test
	public void invalidOwnerAddress() {
		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(SUPERNODE_ADDRESS_INVALID), dbManager);
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
		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(SUPERNODE_ACCOUNT_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + SUPERNODE_ACCOUNT_INVALID + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + SUPERNODE_ACCOUNT_INVALID + " does not exist",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notWitness() {
//    long now = System.currentTimeMillis();
//    AccountCapsule accountCapsule = dbManager.getAccountStore()
//        .get(ByteArray.fromHexString(SUPERNODE_ADDRESS));
//    accountCapsule.setFrozen(1_000_000_000L, now);
//    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(SUPERNODE_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + SUPERNODE_ADDRESS + " is not a witnessAccount");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + SUPERNODE_ADDRESS + " is not a witnessAccount",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noAllowance() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		byte[] address = ByteArray.fromHexString(SUPERNODE_ADDRESS);

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		Assert.assertEquals(accountCapsule.getAllowance(), 0);

		WitnessCapsule witnessCapsule = new WitnessCapsule(
				ByteString.copyFrom(address),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				100, "http://baidu.com");
		dbManager.getWitnessStore().put(address, witnessCapsule);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(SUPERNODE_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("witnessAccount does not have any allowance");

		} catch (ContractValidateException e) {
			Assert.assertEquals("witnessAccount does not have any allowance", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void isGR() {
		Witness w = Args.getInstance().getGenesisBlock().getWitnesses().get(0);
		byte[] address = w.getAddress();
		AccountCapsule grCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("gr"),
						ByteString.copyFrom(address),
						AccountType.Normal,
						initBalance);
		dbManager.getAccountStore().put(grCapsule.createDbKey(), grCapsule);
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		try {
			dbManager.adjustAllowance(address, allowance);
		} catch (BalanceInsufficientException e) {
			Assert.fail("BalanceInsufficientException");
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		Assert.assertEquals(accountCapsule.getAllowance(), allowance);

		WitnessCapsule witnessCapsule = new WitnessCapsule(
				ByteString.copyFrom(address),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				100, "http://google.com");

		dbManager.getAccountStore().put(address, accountCapsule);
		dbManager.getWitnessStore().put(address, witnessCapsule);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(ByteArray.toHexString(address)), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertTrue(dbManager.getWitnessStore().has(address));
		String readableOwnerAddress = StringUtil.createReadableString(address);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + readableOwnerAddress
					+ " is a guard representative and is not allowed to withdraw Balance");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + readableOwnerAddress
					+ " is a guard representative and is not allowed to withdraw Balance", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notTimeToWithdraw() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		byte[] address = ByteArray.fromHexString(SUPERNODE_ADDRESS);
		try {
			dbManager.adjustAllowance(address, allowance);
		} catch (BalanceInsufficientException e) {
			Assert.fail("BalanceInsufficientException");
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		accountCapsule.setLatestWithdrawTime(now);
		Assert.assertEquals(accountCapsule.getAllowance(), allowance);
		Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), now);

		WitnessCapsule witnessCapsule = new WitnessCapsule(
				ByteString.copyFrom(address),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				100, "http://baidu.com");

		dbManager.getAccountStore().put(address, accountCapsule);
		dbManager.getWitnessStore().put(address, witnessCapsule);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
				getContract(SUPERNODE_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("The last withdraw time is " + now + ", less than 24 hours");

		} catch (ContractValidateException e) {
			Assert.assertEquals("The last withdraw time is "
					+ now + ", less than 24 hours", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}

