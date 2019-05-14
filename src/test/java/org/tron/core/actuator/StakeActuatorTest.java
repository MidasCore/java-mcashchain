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
import org.tron.core.capsule.StakeAccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.config.args.Args;
import org.tron.core.controller.StakeAccountController;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.util.ConversionUtil;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;
import java.util.List;

@Slf4j
public class StakeActuatorTest {

	private static final String dbPath = "output_stake_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(1_000_000);
	private static Manager dbManager;
	private static TronApplicationContext context;
	private static StakeAccountController stakeAccountController;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = "c8bc5147ebdf52717278460e39c1ab0d8a2e3d27";
		OWNER_ACCOUNT_INVALID = "78502ce569750cb26ae1e50f1fe708653cede06c";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		stakeAccountController = dbManager.getStakeAccountController();
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
						initBalance);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getStakeAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
	}

	private Any getContract(String ownerAddress, long stakeAmount, long duration) {
		return Any.pack(
				Contract.StakeContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
						.setStakeAmount(stakeAmount)
						.setStakeDuration(duration)
						.build());
	}

	@Test
	public void stakeTest() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager
		);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(
				StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
		long beforeStakeSize = dbManager.getStakeAccountStore().size();
		logger.info(String.valueOf(beforeStakeSize));
		logger.info(String.valueOf(dbManager.getWitnessStore().size()));
		logger.info("[Before] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			long expectedBalanceAfterStaking = initBalance - stakeAmount - ChainConstant.TRANSFER_FEE;

			Assert.assertEquals(owner.getBalance(), expectedBalanceAfterStaking);
			Assert.assertEquals(owner.getStakeAmount(), stakeAmount);

			List<StakeAccountCapsule> stakeAccounts = dbManager.getStakeAccountStore().getAllStakeAccounts();
			for (StakeAccountCapsule sac : stakeAccounts) {
				logger.info(StringUtil.createReadableString(sac.getAddress()));
			}


			Assert.assertEquals(beforeStakeSize, dbManager.getStakeAccountStore().size());

			stakeAccountController.updateStakeAccount();
			Assert.assertEquals(beforeStakeSize + 1, dbManager.getStakeAccountStore().size());

			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());

			stakeAccountController.updateStakeAccount();
			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertNotEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());
			logger.info("[After] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void stakeTimes() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager
		);

		long secondStakeAmount = ConversionUtil.McashToMatoshi(4_000);
		StakeActuator secondActuator = new StakeActuator(
				getContract(OWNER_ADDRESS, secondStakeAmount, duration), dbManager
		);

		TransactionResultCapsule ret = new TransactionResultCapsule();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
		long beforeStakeSize = dbManager.getStakeAccountStore().size();
		logger.info("[Before] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			long expectedBalanceAfterStaking = initBalance - stakeAmount - ChainConstant.TRANSFER_FEE;

			Assert.assertEquals(owner.getBalance(), expectedBalanceAfterStaking);
			Assert.assertEquals(owner.getStakeAmount(), stakeAmount);

			Assert.assertEquals(beforeStakeSize,
					dbManager.getStakeAccountStore().size());
			stakeAccountController.updateStakeAccount();
			Assert.assertEquals(beforeStakeSize,
					dbManager.getStakeAccountStore().size());

			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());

			stakeAccountController.updateStakeAccount();
			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());

			secondActuator.validate();
			secondActuator.execute(ret);

			owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			expectedBalanceAfterStaking = initBalance - stakeAmount - secondStakeAmount
					- 2 * ChainConstant.TRANSFER_FEE;
			Assert.assertEquals(expectedBalanceAfterStaking, owner.getBalance());
			Assert.assertEquals(owner.getStakeAmount(), stakeAmount + secondStakeAmount);
			stakeAccountController.updateStakeAccount();
			Assert.assertEquals(beforeStakeSize + 1,
					dbManager.getStakeAccountStore().size());

			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());
			stakeAccountController.updateStakeAccount();
			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertNotEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());


			logger.info("[After] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void stakeAmountLessThanMinStakeNode() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager
		);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(
				StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
		long beforeStakeSize = dbManager.getStakeAccountStore().size();
		logger.info("[Before] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			long expectedBalanceAfterStaking = initBalance - stakeAmount - ChainConstant.TRANSFER_FEE;

			Assert.assertEquals(owner.getBalance(), expectedBalanceAfterStaking);
			Assert.assertEquals(owner.getStakeAmount(), stakeAmount);

			Assert.assertEquals(beforeStakeSize, dbManager.getStakeAccountStore().size());

			stakeAccountController.updateStakeAccount();
			Assert.assertEquals(beforeStakeSize, dbManager.getStakeAccountStore().size());

			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());

			stakeAccountController.updateStakeAccount();
			accountCapsule = dbManager.getAccountStore().get(
					StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());
			logger.info("[After] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void stakeLessThanZero() {
		long stakeAmount = ConversionUtil.McashToMatoshi(-1_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Stake amount must be positive", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void stakeMoreThanBalance() {
		long stakeAmount = ConversionUtil.McashToMatoshi(10_000_001);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Stake amount must be less than accountBalance", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS_INVALID, stakeAmount, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());

		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAccount() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ACCOUNT_INVALID, stakeAmount, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account [" + OWNER_ACCOUNT_INVALID + "] not exists",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void lessThan1TrxTest() {
		long stakeAmount = 1;
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Stake amount must be more than 1 MCASH", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

//	@Test
//	public void stakeNumTest() {
//		AccountCapsule account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
//		account.setStake(1_000_000L, 1_000_000_000L);
//		account.setStake(2_000_000L, 1_000_000_000L);
//		dbManager.getAccountStore().put(account.getAddress().toByteArray(), account);
//
//		long stakeAmount = 20_000_000L;
//		long duration = 3L;
//		StakeActuator actuator = new StakeActuator(
//				getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
//		TransactionResultCapsule ret = new TransactionResultCapsule();
//		try {
//			actuator.validate();
//			actuator.execute(ret);
//			Assert.fail("cannot run here.");
//		} catch (ContractValidateException e) {
//			Assert.assertEquals("stakesCount must be 0 or 1", e.getMessage());
//		} catch (ContractExeException e) {
//			Assert.fail(e.getMessage());
//		}
//	}
}
