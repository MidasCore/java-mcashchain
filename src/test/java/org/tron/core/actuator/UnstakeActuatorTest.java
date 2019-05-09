package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.*;
import org.tron.core.capsule.utils.StakeUtil;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;

@Slf4j
public class UnstakeActuatorTest {

	private static Manager dbManager;
	private static final String dbPath = "output_unstake_test";
	private static TronApplicationContext context;
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = 10_000_000_000_000L;
	private static final long stakeAmount = 1_000_000_000_000L;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = "548794500882809695a8a687866e76d4271a1abc";
		OWNER_ACCOUNT_INVALID = "548794500882809695a8a687866e76d4271a3456";
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
	public void testUnstake() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setStake(stakeAmount, now);
		Assert.assertEquals(accountCapsule.getStakeAmount(), stakeAmount);
		Assert.assertEquals(accountCapsule.getVotingPower(), StakeUtil.getVotingPowerFromStakeAmount(stakeAmount));
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		StakeAccountCapsule stakeAccountCapsule = new StakeAccountCapsule(accountCapsule.getAddress(),
				StakeUtil.getStakeAmountWithBonusFromStakeAmount(stakeAmount),
				StakeUtil.getVotingPowerFromStakeAmount(stakeAmount));
		dbManager.getStakeAccountStore().put(stakeAccountCapsule.createDbKey(), stakeAccountCapsule);

		UnstakeActuator actuator = new UnstakeActuator(
				getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
//		long totalNetWeightBefore = dbManager.getDynamicPropertiesStore().getTotalNetWeight();

		long stakeAccountListSizeBefore = dbManager.getStakeAccountStore().size();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance + stakeAmount);
			Assert.assertEquals(owner.getFrozenBalance(), 0);
			Assert.assertEquals(owner.getVotingPower(), 0L);
			Assert.assertEquals(dbManager.getStakeAccountStore().size(), stakeAccountListSizeBefore - 1);

//			long totalNetWeightAfter = dbManager.getDynamicPropertiesStore().getTotalNetWeight();
//			Assert.assertEquals(totalNetWeightBefore, totalNetWeightAfter + frozenBalance / 1000_000L);

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
			Assert.fail("Cannot run here.");

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
		accountCapsule.setStake(1_000_000_000L, now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnstakeActuator actuator = new UnstakeActuator(
				getContract(OWNER_ACCOUNT_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account [" + OWNER_ACCOUNT_INVALID + "] does not exist",
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
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("no stake amount", e.getMessage());
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
		accountCapsule.setStake(1_000_000_000L, now + 60000);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnstakeActuator actuator = new UnstakeActuator(
				getContract(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("It's not time to unstake.", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testClearVote() {
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
		accountCapsule.setStake(1_000_000_000_000L, now);
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

