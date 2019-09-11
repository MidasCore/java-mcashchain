package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.StakeAccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter.ChainConstant;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.controller.StakeAccountController;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

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
	private static Wallet wallet;
	private static ApplicationContext context;
	private static StakeAccountController stakeAccountController;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "c8bc5147ebdf52717278460e39c1ab0d8a2e3d27";
		OWNER_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "78502ce569750cb26ae1e50f1fe708653cede06c";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		wallet = context.getBean(Wallet.class);
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
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			long expectedBalanceAfterStaking = initBalance - stakeAmount - ChainConstant.TRANSFER_FEE;

			Assert.assertEquals(owner.getBalance(), expectedBalanceAfterStaking);
			Assert.assertEquals(owner.getNormalStakeAmount(), stakeAmount);

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
			Assert.assertNotEquals(0, accountCapsule.getAllowance());

			long blockNumber = dbManager.getHeadBlockNum() + 1;
			byte[] key = ByteArray.fromLong(blockNumber);

			Assert.assertTrue(dbManager.getBlockRewardStore().has(key));

			GrpcAPI.BlockRewardList rewardList = wallet.getBlockReward(blockNumber);

			for (Protocol.BlockReward.Reward reward : rewardList.getRewardsList()) {
				logger.info(String.valueOf(reward.getAmount()));
			}

			Assert.assertEquals(1, rewardList.getRewardsCount());
			logger.info("[After] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getBlockRewardStore().reset();
		}
	}

	@Test
	public void stakeTimes() {
		long stakeAmount = ConversionUtil.McashToMatoshi(5_000);
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
			getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager
		);

		long secondStakeAmount = ConversionUtil.McashToMatoshi(10_000);
		StakeActuator secondActuator = new StakeActuator(
			getContract(OWNER_ADDRESS, secondStakeAmount, duration), dbManager
		);

		TransactionResultCapsule ret = new TransactionResultCapsule();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
		long beforeStakeSize = dbManager.getStakeAccountStore().size();
		logger.info("[Before] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		logger.info("before stake size = {}", beforeStakeSize);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			long expectedBalanceAfterStaking = initBalance - stakeAmount - ChainConstant.TRANSFER_FEE;

			Assert.assertEquals(owner.getBalance(), expectedBalanceAfterStaking);
			Assert.assertEquals(owner.getNormalStakeAmount(), stakeAmount);

			Assert.assertEquals(beforeStakeSize, dbManager.getStakeAccountStore().size());
			stakeAccountController.updateStakeAccount();
			Assert.assertEquals(beforeStakeSize + 1, dbManager.getStakeAccountStore().size());

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
			Assert.assertEquals(owner.getNormalStakeAmount(), stakeAmount + secondStakeAmount);
			stakeAccountController.updateStakeAccount();
			Assert.assertEquals(beforeStakeSize + 1,
				dbManager.getStakeAccountStore().size());

			accountCapsule = dbManager.getAccountStore().get(
				StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertEquals(expectedBalanceAfterStaking, accountCapsule.getBalance());
			stakeAccountController.updateStakeAccount();
			accountCapsule = dbManager.getAccountStore().get(
				StringUtil.hexString2ByteString(OWNER_ADDRESS).toByteArray());
			Assert.assertNotEquals(0, accountCapsule.getAllowance());


			logger.info("[After] {} balance = {}", OWNER_ADDRESS, accountCapsule.getBalance());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getBlockRewardStore().reset();
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
		long stakeAmount = ConversionUtil.McashToMatoshi(20_000_000);
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
	public void stakeAmountLessThanMinStakeNode() {
		long stakeAmount = 100000000;
		long duration = 3;
		StakeActuator actuator = new StakeActuator(
			getContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Stake amount must be more than or equal 5000 MCASH", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}
