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
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class WitnessResignActuatorTest {

	private static final String dbPath = "output_witness_resign_test";
	private static final String SUPERNODE_ADDRESS;
	private static final String SUPERNODE_ADDRESS_ACCOUNT_NAME = "test_account";
	private static final String SUPERNODE_ADDRESS_NOT_WITNESS;
	private static final String SUPERNODE_ADDRESS_NOT_WITNESS_ACCOUNT_NAME = "test_account1";
	private static final String SUPERNODE_ADDRESS_NOTEXIST;
	private static final String OWNER_ADDRESS;
	private static final String OTHER_OWNER_ADDRESS;
	private static final String URL = "https://mcash.network";
	private static final String ADDRESS_INVALID = "aaaa";
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_NOTEXIST = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_NOT_WITNESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "4536f33f3e0a725484738017c533f877d5df3a82";
		OTHER_OWNER_ADDRESS = Wallet.getAddressPreFixString() + "78502ce569750cb26ae1e50f1fe708653cede06c";
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
		// address in accountStore and witnessStore
		AccountCapsule accountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS)),
				ByteString.copyFromUtf8(SUPERNODE_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(ByteArray.fromHexString(SUPERNODE_ADDRESS), accountCapsule);

		AccountCapsule ownerAccountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				ByteString.copyFromUtf8(SUPERNODE_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		ownerAccountCapsule.setWitnessStake(Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT);
		dbManager.getAccountStore().put(ownerAccountCapsule.createDbKey(), ownerAccountCapsule);

		WitnessCapsule witnessCapsule = new WitnessCapsule(
			ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS)),
			ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
			10_000_000L, URL);
		dbManager.getWitnessStore().put(ByteArray.fromHexString(SUPERNODE_ADDRESS), witnessCapsule);

		// address exist in accountStore, but is not witness
		AccountCapsule accountNotWitnessCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_NOT_WITNESS)),
				ByteString.copyFromUtf8(SUPERNODE_ADDRESS_NOT_WITNESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore()
			.put(ByteArray.fromHexString(SUPERNODE_ADDRESS_NOT_WITNESS), accountNotWitnessCapsule);
		dbManager.getWitnessStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_NOT_WITNESS));
		// address does not exist in accountStore
		dbManager.getAccountStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_NOTEXIST));
	}

	private Any getContract(String address, String supernodeAddress) {
		return Any.pack(
			Contract.WitnessResignContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(supernodeAddress)))
				.build());
	}

	private Any getCreateWitnessContract(String address, String witnessAddress, String url) {
		return Any.pack(
			Contract.WitnessCreateContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(witnessAddress)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(url)))
				.build()
		);
	}

	@Test
	public void myTest() {
		String ownerAddressString = Wallet.getAddressPreFixString() + "bf82fd6597cd3200c468220ecd7cf47c1a4cb149";
		String witnessAddressString = Wallet.getAddressPreFixString() + "db46eb291a46318d29a8a23bdc9dec75ea51ef32";

		byte[] witnessAddressByteArray = ByteArray.fromHexString(witnessAddressString);
		ByteString witnessAddressByteString = ByteString.copyFrom(witnessAddressByteArray);

		dbManager.getWitnessStore().delete(ByteArray.fromHexString(witnessAddressString));

		AccountCapsule ownerAccountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(ownerAddressString)),
				ByteString.copyFromUtf8("owner"),
				Protocol.AccountType.Normal);
		ownerAccountCapsule.setStake(Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT, 1);
		ownerAccountCapsule.setBalance(dbManager.getDynamicPropertiesStore().getAccountUpgradeCost());
		dbManager.getAccountStore().put(ownerAccountCapsule.createDbKey(), ownerAccountCapsule);

		AccountCapsule witnessAccountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(witnessAddressString)),
				ByteString.copyFromUtf8("witness"),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(witnessAccountCapsule.createDbKey(), witnessAccountCapsule);

		WitnessCreateActuator witnessCreateActuator = new WitnessCreateActuator(getCreateWitnessContract(
			ownerAddressString, witnessAddressString, "https://midasprotocol.io"), dbManager);

		WitnessResignActuator witnessResignActuator = new WitnessResignActuator(
			getContract(ownerAddressString, witnessAddressString),
			dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			witnessCreateActuator.validate();
			witnessCreateActuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			dbManager.getWitnessController().updateWitness();
			Assert.assertTrue(dbManager.getWitnessScheduleStore().getActiveWitnesses().contains(witnessAddressByteString));

			witnessResignActuator.validate();
			witnessResignActuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertFalse(dbManager.getWitnessScheduleStore().getActiveWitnesses().contains(witnessAddressByteString));
			dbManager.getWitnessController().updateWitness();
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Update witness,result is success.
	 */
	@Test
	public void rightResignWitness() {
		WitnessResignActuator actuator = new WitnessResignActuator(getContract(OWNER_ADDRESS, SUPERNODE_ADDRESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		byte[] witnessAddress = ByteArray.fromHexString(SUPERNODE_ADDRESS);
		List<ByteString> activeWitnesses = new ArrayList<>();
		activeWitnesses.add(ByteString.copyFrom(witnessAddress));
		dbManager.getWitnessScheduleStore().saveActiveWitnesses(activeWitnesses);
		Assert.assertTrue(dbManager.getWitnessScheduleStore().getActiveWitnesses().contains(ByteString.copyFrom(witnessAddress)));
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			Assert.assertTrue(dbManager.getWitnessStore().has(witnessAddress));
			WitnessCapsule witnessCapsule = dbManager.getWitnessStore().get(witnessAddress);
			Assert.assertEquals(Protocol.Witness.Status.RESIGNED, witnessCapsule.getStatus());
			Assert.assertFalse(dbManager.getWitnessScheduleStore().getActiveWitnesses().contains(ByteString.copyFrom(witnessAddress)));

			AccountCapsule ownerAccountCapsule = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ownerAccountCapsule.getTotalStakeAmount(), ownerAccountCapsule.getNormalStakeAmount());
			Assert.assertEquals(0, ownerAccountCapsule.getWitnessStakeAmount());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		WitnessResignActuator actuator = new WitnessResignActuator(getContract(ADDRESS_INVALID, SUPERNODE_ADDRESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
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
	public void invalidSupernodeAddress() {
		WitnessResignActuator actuator = new WitnessResignActuator(
			getContract(OWNER_ADDRESS, ADDRESS_INVALID),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid witnessAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid witnessAddress", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noneExistWitness() {
		WitnessResignActuator actuator = new WitnessResignActuator(
			getContract(OWNER_ADDRESS, SUPERNODE_ADDRESS_NOT_WITNESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noneExistAccount() {
		WitnessResignActuator actuator = new WitnessResignActuator(
			getContract(OWNER_ADDRESS, SUPERNODE_ADDRESS_NOTEXIST),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness account does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness account does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void ownerNotOwnSupernode() {
		AccountCapsule otherOwnerAccountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OTHER_OWNER_ADDRESS)),
				ByteString.copyFromUtf8(SUPERNODE_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(otherOwnerAccountCapsule.createDbKey(), otherOwnerAccountCapsule);

		WitnessResignActuator actuator = new WitnessResignActuator(
			getContract(OTHER_OWNER_ADDRESS, SUPERNODE_ADDRESS),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Address " + OTHER_OWNER_ADDRESS +
				" does not own witness " + SUPERNODE_ADDRESS);
		} catch (ContractValidateException e) {
			Assert.assertEquals("Address " + OTHER_OWNER_ADDRESS +
				" does not own witness " + SUPERNODE_ADDRESS, e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}