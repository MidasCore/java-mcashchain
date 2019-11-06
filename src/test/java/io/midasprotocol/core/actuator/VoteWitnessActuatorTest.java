package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.config.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.controller.StakeAccountController;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.core.witness.WitnessController;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.VoteWitnessContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

import static junit.framework.TestCase.fail;

@Slf4j
public class VoteWitnessActuatorTest {

	private static final String dbPath = "output_vote_witness_test";
	private static final String ACCOUNT_NAME = "account";
	private static final String OWNER_ADDRESS;
	private static final String WITNESS_NAME = "witness";
	private static final String WITNESS_ADDRESS;
	private static final String WITNESS_ADDRESS_2;
	private static final String WITNESS_OWNER_ADDRESS;
	private static final String URL = "https://mcash.network";
	private static final String ADDRESS_INVALID = "aaaa";
	private static final String WITNESS_ADDRESS_NOACCOUNT;
	private static final String OWNER_ADDRESS_NOACCOUNT;
	private static ApplicationContext context;
	private static Manager dbManager;
	private static WitnessController witnessController;
	private static StakeAccountController stakeAccountController;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		WITNESS_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		WITNESS_ADDRESS_2 = Wallet.getAddressPreFixString() + "84ca19269c61f4778e51a8ed085620d7ac1fc2ea";
		WITNESS_ADDRESS_NOACCOUNT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
		OWNER_ADDRESS_NOACCOUNT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aae";
		WITNESS_OWNER_ADDRESS = Wallet.getAddressPreFixString() + "84ca19269c61f4778e51a8ed085620d7ac1fc2ea";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		witnessController = dbManager.getWitnessController();
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

	private Any getContract(String address, String voteaddress) {
		return Any.pack(
			VoteWitnessContract.newBuilder()
				.setOwnerAddress(StringUtil.hexString2ByteString(address))
				.setVoteAddress(StringUtil.hexString2ByteString(voteaddress))
				.build());
	}

	/**
	 * create temp Capsule test need.
	 */
	@Before
	public void createCapsule() {
		WitnessCapsule ownerCapsule =
			new WitnessCapsule(
				StringUtil.hexString2ByteString(WITNESS_ADDRESS),
				StringUtil.hexString2ByteString(WITNESS_OWNER_ADDRESS),
				10L,
				URL);
		AccountCapsule witnessAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(WITNESS_NAME),
				StringUtil.hexString2ByteString(WITNESS_ADDRESS),
				AccountType.Normal,
				300L);
		AccountCapsule ownerAccountFirstCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME),
				StringUtil.hexString2ByteString(OWNER_ADDRESS),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(10_000_000));

		dbManager.getAccountStore()
			.put(witnessAccountSecondCapsule.getAddress().toByteArray(), witnessAccountSecondCapsule);
		dbManager.getAccountStore()
			.put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
		dbManager.getWitnessStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
	}

	private Any getRepeatedContract(String address, String voteaddress, int times) {
		VoteWitnessContract.Builder builder = VoteWitnessContract.newBuilder();
		builder.setOwnerAddress(StringUtil.hexString2ByteString(address));
		for (int i = 0; i < times; i++) {
			builder.setVoteAddress(StringUtil.hexString2ByteString(voteaddress));
		}
		return Any.pack(builder.build());
	}

	private Any getStakeContract(String ownerAddress, long stakeAmount, long duration) {
		return Any.pack(
			Contract.StakeContract.newBuilder()
				.setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
				.setStakeAmount(stakeAmount)
				.setStakeDuration(duration)
				.build());
	}

	private Any getUnstakeContract(String ownerAddress) {
		return Any.pack(
			Contract.UnstakeContract.newBuilder()
				.setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
				.build());
	}

	/**
	 * voteWitness,result is success.
	 */
	@Test
	public void voteWitness() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(1800,
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteCount());
			Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteAddress().toByteArray());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10 + 1800, witnessCapsule.getVoteCount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Invalid ownerAddress voteWitness,result is failed,exception is "Invalid address".
	 */
	@Test
	public void invalidAddress() {
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(ADDRESS_INVALID, WITNESS_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid address");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10, witnessCapsule.getVoteCount());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	/**
	 * use AccountStore not exists witness Address VoteWitness,result is failed,exception is "account
	 * not exists".
	 */
	@Test
	public void noAccount() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOACCOUNT),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + WITNESS_ADDRESS_NOACCOUNT + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote());
			Assert.assertEquals("Witness " + WITNESS_ADDRESS_NOACCOUNT + " does not exist", e.getMessage());
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10, witnessCapsule.getVoteCount());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	/**
	 * use WitnessStore not exists Address VoteWitness,result is failed,exception is "Witness not
	 * exists".
	 */
	@Test
	public void noWitness() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		AccountCapsule accountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(WITNESS_NAME),
				StringUtil.hexString2ByteString(WITNESS_ADDRESS_NOACCOUNT),
				AccountType.Normal,
				300L);
		dbManager.getAccountStore()
			.put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOACCOUNT),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + OWNER_ADDRESS_NOACCOUNT + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote());
			Assert.assertEquals("Witness " + WITNESS_ADDRESS_NOACCOUNT + " does not exist", e.getMessage());
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10, witnessCapsule.getVoteCount());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * invalidVoteAddress
	 */
	@Test
	public void invalidVoteAddress() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		AccountCapsule accountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(WITNESS_NAME),
				StringUtil.hexString2ByteString(WITNESS_ADDRESS_NOACCOUNT),
				AccountType.Normal,
				300L);
		dbManager.getAccountStore()
			.put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, ADDRESS_INVALID),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid vote address");
		} catch (ContractValidateException e) {
			Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote());
			Assert.assertEquals("Invalid vote address", e.getMessage());
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10, witnessCapsule.getVoteCount());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Vote 1 witness one more times.
	 */
	@Test
	public void vote1WitnessOneMoreTimes() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		VoteWitnessActuator actuator = new VoteWitnessActuator(
			getRepeatedContract(OWNER_ADDRESS, WITNESS_ADDRESS, 30),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);

			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10 + 1800, witnessCapsule.getVoteCount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use AccountStore not exists ownerAddress VoteWitness,result is failed,exception is "account not
	 * exists".
	 */
	@Test
	public void noOwnerAccount() {
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS_NOACCOUNT, WITNESS_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + OWNER_ADDRESS_NOACCOUNT + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ADDRESS_NOACCOUNT + " does not exist", e.getMessage());
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10, witnessCapsule.getVoteCount());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Twice voteWitness,result is the last voteWitness.
	 */
	@Test
	public void voteWitnessTwice() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS), dbManager);
		VoteWitnessActuator actuatorTwice =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			actuatorTwice.validate();
			actuatorTwice.execute(ret);
			Assert.assertEquals(1800,
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteCount());
			Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteAddress().toByteArray());

			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10 + 1800, witnessCapsule.getVoteCount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void voteOtherWitnessInEpoch() {
		{
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			logger.info("[Before] Witness 1 vote: {}", witnessCapsule.getVoteCount());
			WitnessCapsule witnessCapsule2 = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS_2));
			Assert.assertEquals(104, witnessCapsule2.getVoteCount());
			logger.info("[Before] Witness 2 vote: {}", witnessCapsule2.getVoteCount());
		}

		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS), dbManager);
		VoteWitnessActuator actuatorTwice =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_2), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			actuatorTwice.validate();
			actuatorTwice.execute(ret);
			Assert.assertEquals(1800,
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteCount());
			Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS_2),
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
					.getVote().getVoteAddress().toByteArray());

			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			witnessController.updateWitness();

			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			WitnessCapsule witnessCapsule2 = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS_2));

			logger.info("[After] Witness 1 vote: {}", witnessCapsule.getVoteCount());
			logger.info("[After] Witness 2 vote: {}", witnessCapsule2.getVoteCount());

			Assert.assertEquals(10, witnessCapsule.getVoteCount());
			Assert.assertEquals(104 + 1800, witnessCapsule2.getVoteCount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void unstake() {
		long stakeAmount = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		StakeActuator stakeActuator = new StakeActuator(
			getStakeContract(OWNER_ADDRESS, stakeAmount, duration), dbManager);
		VoteWitnessActuator actuator =
			new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		UnstakeActuator unstakeActuator = new UnstakeActuator(
			getUnstakeContract(OWNER_ADDRESS), dbManager
		);
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1);
		try {
			stakeActuator.validate();
			stakeActuator.execute(ret);
			stakeAccountController.updateStakeAccount();
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(1800,
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteCount());
			Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteAddress().toByteArray());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			witnessController.updateWitness();
			WitnessCapsule witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10 + 1800, witnessCapsule.getVoteCount());

			dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
				1 + dbManager.getDynamicPropertiesStore().getStakeTimeInDay() * Parameter.TimeConstant.MS_PER_DAY
			);
			unstakeActuator.validate();
			unstakeActuator.execute(ret);
			witnessController.updateWitness();
			witnessCapsule = witnessController
				.getWitnessByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
			Assert.assertEquals(10, witnessCapsule.getVoteCount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}