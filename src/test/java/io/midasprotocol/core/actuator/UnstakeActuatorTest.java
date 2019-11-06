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
import io.midasprotocol.core.capsule.StakeAccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.VoteChangeCapsule;
import io.midasprotocol.core.util.StakeUtil;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class UnstakeActuatorTest {
	private static final String dbPath = "output_unstake_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(10_000_000);
	private static final long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
	private static final long supernodeStakeAmount = ConversionUtil.McashToMatoshi(5_000_000);
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
				initBalance);
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);

		dbManager.getStakeAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
	}

	private Any getContract(String ownerAddress) {
		return Any.pack(
			Contract.UnstakeContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.build());
	}


	@Test
	public void normalUnstake() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setStake(stakeAmount, now);
		Assert.assertEquals(accountCapsule.getNormalStakeAmount(), stakeAmount);
		Assert.assertEquals(accountCapsule.getVotingPower(), StakeUtil.getVotingPowerFromStakeAmount(stakeAmount));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		StakeAccountCapsule stakeAccountCapsule = new StakeAccountCapsule(accountCapsule.getAddress(),
			StakeUtil.getStakeAmountWithBonusFromStakeAmount(stakeAmount),
			StakeUtil.getVotingPowerFromStakeAmount(stakeAmount));
		dbManager.getStakeAccountStore().put(stakeAccountCapsule.createDbKey(), stakeAccountCapsule);

		UnstakeActuator actuator = new UnstakeActuator(
			getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
//		long totalNetWeightBefore = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();

		long stakeAccountListSizeBefore = dbManager.getStakeAccountStore().size();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance + stakeAmount);
			Assert.assertEquals(owner.getFrozenBalanceForBandwidth(), 0);
			Assert.assertEquals(owner.getVotingPower(), 0L);
			Assert.assertEquals(dbManager.getStakeAccountStore().size(), stakeAccountListSizeBefore - 1);

//			long totalNetWeightAfter = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();
//			Assert.assertEquals(totalNetWeightBefore, totalNetWeightAfter + frozenBalance / 1000_000L);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void witnessOwnerUnstakeAfterConstantinople() {
		dbManager.getDynamicPropertiesStore().saveAllowVmConstantinople(1);
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		long totalStakeAmount = stakeAmount + supernodeStakeAmount;
		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setStake(stakeAmount, now);
		accountCapsule.setWitnessStake(supernodeStakeAmount);
		Assert.assertEquals(accountCapsule.getNormalStakeAmount(), stakeAmount);
		Assert.assertEquals(accountCapsule.getTotalStakeAmount(), totalStakeAmount);
		Assert.assertEquals(accountCapsule.getVotingPower(), StakeUtil.getVotingPowerFromStakeAmount(totalStakeAmount));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		StakeAccountCapsule stakeAccountCapsule = new StakeAccountCapsule(accountCapsule.getAddress(),
			StakeUtil.getStakeAmountWithBonusFromStakeAmount(totalStakeAmount),
			StakeUtil.getVotingPowerFromStakeAmount(totalStakeAmount));
		dbManager.getStakeAccountStore().put(stakeAccountCapsule.createDbKey(), stakeAccountCapsule);

		UnstakeActuator actuator = new UnstakeActuator(
			getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		long stakeAccountListSizeBefore = dbManager.getStakeAccountStore().size();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(initBalance + stakeAmount, owner.getBalance());
			Assert.assertEquals(supernodeStakeAmount, owner.getTotalStakeAmount());
			Assert.assertNotEquals(0L, owner.getVotingPower());
			Assert.assertEquals(stakeAccountListSizeBefore, dbManager.getStakeAccountStore().size());

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setStake(stakeAmount, now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnstakeActuator actuator = new UnstakeActuator(
			getContract(OWNER_ADDRESS_INVALID), dbManager);
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
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setStake(ConversionUtil.McashToMatoshi(1_000), now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnstakeActuator actuator = new UnstakeActuator(
			getContract(OWNER_ACCOUNT_INVALID), dbManager);
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
	public void noStake() {
		UnstakeActuator actuator = new UnstakeActuator(getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No stake amount");
		} catch (ContractValidateException e) {
			Assert.assertEquals("No stake amount", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notTimeToUnstake() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setStake(ConversionUtil.McashToMatoshi(1_000), now + 60000);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnstakeActuator actuator = new UnstakeActuator(
			getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("It's not time to unstake");
		} catch (ContractValidateException e) {
			Assert.assertEquals("It's not time to unstake", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void clearVoteTest() {
		byte[] ownerAddressBytes = ByteArray.fromHexString(OWNER_ADDRESS);
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddressBytes);
		accountCapsule.setStake(stakeAmount, now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		UnstakeActuator actuator = new UnstakeActuator(
			getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		dbManager.getVoteChangeStore().reset();
		Assert.assertNull(dbManager.getVoteChangeStore().get(ownerAddressBytes));
		try {
			actuator.validate();
			actuator.execute(ret);
			VoteChangeCapsule voteChangeCapsule = dbManager.getVoteChangeStore().get(ownerAddressBytes);
			Assert.assertNotNull(voteChangeCapsule);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// if had votes
		VoteChangeCapsule voteChangeCapsule = new VoteChangeCapsule(
			ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
		voteChangeCapsule.setNewVote(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
			100);
		dbManager.getVoteChangeStore().put(ByteArray.fromHexString(OWNER_ADDRESS), voteChangeCapsule);
		accountCapsule.setStake(ConversionUtil.McashToMatoshi(1_000_000), now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		try {
			actuator.validate();
			actuator.execute(ret);
			voteChangeCapsule = dbManager.getVoteChangeStore().get(ownerAddressBytes);
			Assert.assertNotNull(voteChangeCapsule);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

}

