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
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.util.StakeUtil;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;

import static junit.framework.TestCase.fail;

@Slf4j

public class WitnessCreateActuatorTest {

	private static final String dbPath = "output_witness_create_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String SUPERNODE_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String SUPERNODE_ADDRESS_SECOND;
	private static final String ACCOUNT_NAME_THIRD = "ownerM";
	private static final String SUPERNODE_ADDRESS_THIRD;
	private static final String OWNER_ADDRESS;
	private static final String URL = "https://midasprotocol.io";
	private static final String SUPERNODE_ADDRESS_INVALID = "aaaa";
	private static final String SUPERNODE_ADDRESS_NOACCOUNT;
	private static final String OWNER_ADDRESS_BALANCENOTSUFFIENT;
	private static final String NEW_OWNER_ADDRESS;
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS_FIRST = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_SECOND = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_THIRD = Wallet.getAddressPreFixString() + "12e488654c2e7fc606c268ca19fbfa8d1c6a35da";
		SUPERNODE_ADDRESS_NOACCOUNT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
		OWNER_ADDRESS_BALANCENOTSUFFIENT = Wallet.getAddressPreFixString() + "78502ce569750cb26ae1e50f1fe708653cede06c";
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "84ca19269c61f4778e51a8ed085620d7ac1fc2ea";
		NEW_OWNER_ADDRESS = Wallet.getAddressPreFixString() + "4536f33f3e0a725484738017c533f877d5df3a82";
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
		WitnessCapsule ownerCapsule =
			new WitnessCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				10_000_000L,
				URL);
		AccountCapsule ownerAccountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(""),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(200_000));
		ownerAccountCapsule.setWitnessStake(500000000000000L);
		ownerAccountCapsule.setStake(1000000000000000L, 0);

		AccountCapsule supernodeAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)),
				AccountType.Normal,
				0);
		AccountCapsule supernodeAccountFirstCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_FIRST),
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND)),
				AccountType.Normal,
				0);
		AccountCapsule supernodeAccountThirdCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_THIRD),
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_THIRD)),
				AccountType.Normal,
				0);

		dbManager.getAccountStore()
			.put(ownerAccountCapsule.getAddress().toByteArray(), ownerAccountCapsule);
		dbManager.getAccountStore()
			.put(supernodeAccountSecondCapsule.getAddress().toByteArray(), supernodeAccountSecondCapsule);
		dbManager.getAccountStore()
			.put(supernodeAccountFirstCapsule.getAddress().toByteArray(), supernodeAccountFirstCapsule);
		dbManager.getAccountStore()
			.put(supernodeAccountThirdCapsule.getAddress().toByteArray(), supernodeAccountThirdCapsule);

		dbManager.getWitnessStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getWitnessStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND));
		dbManager.getWitnessStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_THIRD));
	}

	private Any getContract(String address, String ownerAddress, String url) {
		return Any.pack(
			Contract.WitnessCreateContract.newBuilder()
				.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(url)))
				.build());
	}

	private Any getContract(String address, String ownerAddress, ByteString url) {
		return Any.pack(
			Contract.WitnessCreateContract.newBuilder()
				.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setUrl(url)
				.build());
	}

	/**
	 * first createWitness,result is success.
	 */
	@Test
	public void firstCreateWitness() {
		WitnessCreateActuator actuator =
			new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + SUPERNODE_ADDRESS_SECOND + " has existed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness " + SUPERNODE_ADDRESS_FIRST + " has existed", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void secondCreateWitness() {
		WitnessCreateActuator actuator =
			new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_SECOND, OWNER_ADDRESS, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + OWNER_ADDRESS + " owns other supernode");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ADDRESS + " owns other supernode", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void createWitness() {
		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(""),
				ByteString.copyFrom(ByteArray.fromHexString(NEW_OWNER_ADDRESS)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(200_000));
		long startingStakeAmount = 10000000000000000L;
		ownerCapsule.setStake(startingStakeAmount, 0);
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);

		WitnessCreateActuator actuator =
			new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_THIRD, NEW_OWNER_ADDRESS, URL),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			ownerCapsule = dbManager.getAccountStore().get(ByteArray.fromHexString(NEW_OWNER_ADDRESS));
			Assert.assertEquals(startingStakeAmount - Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT,
				ownerCapsule.getNormalStakeAmount());
			Assert.assertEquals(Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT,
				ownerCapsule.getWitnessStakeAmount());
			Assert.assertEquals(startingStakeAmount, ownerCapsule.getTotalStakeAmount());

			dbManager.getWitnessController().updateWitness();
			WitnessCapsule witnessCapsule =
				dbManager.getWitnessStore().get(ByteArray.fromHexString(SUPERNODE_ADDRESS_THIRD));
			Assert.assertNotNull(witnessCapsule);
			long votingPower = StakeUtil.getVotingPowerFromStakeAmount(ownerCapsule.getTotalStakeAmount());
			Assert.assertEquals(votingPower, witnessCapsule.getVoteCount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Invalid Address createWitness,result is failed,exception is "Invalid address".
	 */
	@Test
	public void invalidAddress() {
		WitnessCreateActuator actuator =
			new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_INVALID, OWNER_ADDRESS, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid supernodeAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid supernodeAddress", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Invalid url createWitness,result is failed,exception is "Invalid url".
	 */
	@Test
	public void invalidUrlTest() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		//Url cannot empty
		try {
			WitnessCreateActuator actuator = new WitnessCreateActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS, ByteString.EMPTY), dbManager);
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//256 bytes
		String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		//Url length can not greater than 256
		try {
			WitnessCreateActuator actuator = new WitnessCreateActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS, ByteString.copyFromUtf8(url256Bytes + "0")), dbManager);
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use AccountStore not exists Address createWitness,result is failed,exception is "account not
	 * exists".
	 */
	@Test
	public void noAccount() {
		WitnessCreateActuator actuator =
			new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_NOACCOUNT, OWNER_ADDRESS, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + SUPERNODE_ADDRESS_NOACCOUNT + " does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	/**
	 * use Account  ,result is failed,exception is "account not exists".
	 */
	@Test
	public void balanceNotSufficient() {
		AccountCapsule balanceNotSufficientCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("balanceNotSufficient"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_BALANCENOTSUFFIENT)),
				AccountType.Normal,
				50L);
		dbManager.getAccountStore()
			.put(balanceNotSufficientCapsule.getAddress().toByteArray(), balanceNotSufficientCapsule);

		WitnessCreateActuator actuator =
			new WitnessCreateActuator(getContract(
				SUPERNODE_ADDRESS_SECOND,
				OWNER_ADDRESS_BALANCENOTSUFFIENT,
				URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("balance < AccountUpgradeCost");
		} catch (ContractValidateException e) {
			Assert.assertEquals("balance < AccountUpgradeCost", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}