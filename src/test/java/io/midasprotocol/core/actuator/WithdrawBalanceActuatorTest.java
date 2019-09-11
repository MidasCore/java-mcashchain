package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.StakeAccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;

import java.io.File;

@Slf4j
public class WithdrawBalanceActuatorTest {

	private static final String dbPath = "output_withdraw_balance_test";
	private static final String WITNESS_ADDRESS;
	private static final String WITNESS_ADDRESS_INVALID = "aaaa";
	private static final String WITNESS_ACCOUNT_INVALID;
	private static final String OWNER_ADDRESS;
	private static final long initBalance = 1_000_000_000_000L;
	private static final long allowance = 3_200_000_000L;
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		WITNESS_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		WITNESS_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "4536f33f3e0a725484738017c533f877d5df3a82";
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
		dbManager.getAccountStore().delete(ByteArray.fromHexString(WITNESS_ADDRESS));
		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(WITNESS_ADDRESS)),
				AccountType.Normal,
				initBalance);
		ownerCapsule.setStake(1000000000L, 0);
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
		dbManager.getStakeAccountStore().put(ownerCapsule.createDbKey(),
			new StakeAccountCapsule(ByteString.copyFrom(ByteArray.fromHexString(WITNESS_ADDRESS)),
				initBalance, 0));
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
		byte[] address = ByteArray.fromHexString(WITNESS_ADDRESS);
		try {
			dbManager.adjustAllowance(address, allowance);
		} catch (BalanceInsufficientException e) {
			Assert.fail("BalanceInsufficientException");
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		Assert.assertEquals(accountCapsule.getAllowance(), allowance);
		Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), 0);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
			getContract(WITNESS_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(WITNESS_ADDRESS));

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
			getContract(WITNESS_ADDRESS_INVALID), dbManager);
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
			getContract(WITNESS_ACCOUNT_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + WITNESS_ACCOUNT_INVALID + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + WITNESS_ACCOUNT_INVALID + " does not exist",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notHaveAllowance() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

//    AccountCapsule accountCapsule = dbManager.getAccountStore()
//        .get(ByteArray.fromHexString(WITNESS_ADDRESS));
//    accountCapsule.setFrozenForBandwidth(1_000_000_000L, now);
//    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
			getContract(WITNESS_ADDRESS), dbManager);
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
	public void noAllowance() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		byte[] address = ByteArray.fromHexString(WITNESS_ADDRESS);

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		Assert.assertEquals(accountCapsule.getAllowance(), 0);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
			getContract(WITNESS_ADDRESS), dbManager);
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

//	@Test
//	public void isGR() {
//		Witness w = Args.getInstance().getGenesisBlock().getWitnesses().get(0);
//		byte[] address = w.getAddress();
//		AccountCapsule grCapsule =
//				new AccountCapsule(
//						ByteString.copyFromUtf8("gr"),
//						ByteString.copyFrom(address),
//						AccountType.Normal,
//						initBalance);
//		dbManager.getAccountStore().put(grCapsule.createDbKey(), grCapsule);
//		long now = System.currentTimeMillis();
//		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
//
//		try {
//			dbManager.adjustAllowance(address, allowance);
//		} catch (BalanceInsufficientException e) {
//			Assert.fail("BalanceInsufficientException");
//		}
//		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
//		Assert.assertEquals(accountCapsule.getAllowance(), allowance);
//
//		WitnessCapsule witnessCapsule = new WitnessCapsule(
//				ByteString.copyFrom(address),
//				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
//				100, "http://google.com");
//
//		dbManager.getAccountStore().put(address, accountCapsule);
//		dbManager.getWitnessStore().put(address, witnessCapsule);
//
//		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
//				getContract(ByteArray.toHexString(address)), dbManager);
//		TransactionResultCapsule ret = new TransactionResultCapsule();
//		Assert.assertTrue(dbManager.getWitnessStore().has(address));
//		String readableOwnerAddress = StringUtil.createReadableString(address);
//		try {
//			actuator.validate();
//			actuator.execute(ret);
//			Assert.fail("Account " + readableOwnerAddress
//					+ " is a guard representative and is not allowed to withdraw Balance");
//
//		} catch (ContractValidateException e) {
//			Assert.assertEquals("Account " + readableOwnerAddress
//					+ " is a guard representative and is not allowed to withdraw Balance", e.getMessage());
//		} catch (ContractExeException e) {
//			Assert.fail(e.getMessage());
//		}
//	}

	@Test
	public void notTimeToWithdraw() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		byte[] address = ByteArray.fromHexString(WITNESS_ADDRESS);
		try {
			dbManager.adjustAllowance(address, allowance);
		} catch (BalanceInsufficientException e) {
			Assert.fail("BalanceInsufficientException");
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		accountCapsule.setLatestWithdrawTime(now);
		Assert.assertEquals(accountCapsule.getAllowance(), allowance);
		Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), now);

		dbManager.getAccountStore().put(address, accountCapsule);

		WithdrawBalanceActuator actuator = new WithdrawBalanceActuator(
			getContract(WITNESS_ADDRESS), dbManager);
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

