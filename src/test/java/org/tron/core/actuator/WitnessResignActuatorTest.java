package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.Parameter;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;

@Slf4j
public class WitnessResignActuatorTest {

	private static final String dbPath = "output_WitnessUpdate_test";
	private static final String SUPERNODE_ADDRESS;
	private static final String SUPERNODE_ADDRESS_ACCOUNT_NAME = "test_account";
	private static final String SUPERNODE_ADDRESS_NOT_WITNESS;
	private static final String SUPERNODE_ADDRESS_NOT_WITNESS_ACCOUNT_NAME = "test_account1";
	private static final String SUPERNODE_ADDRESS_NOTEXIST;
	private static final String OWNER_ADDRESS;
	private static final String OTHER_OWNER_ADDRESS;
	private static final String URL = "https://tron.network";
	private static final String ADDRESS_INVALID = "aaaa";
	private static TronApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_NOTEXIST = "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_NOT_WITNESS = "548794500882809695a8a687866e76d427122222";
		OWNER_ADDRESS = "4536f33f3e0a725484738017c533f877d5df3a82";
		OTHER_OWNER_ADDRESS = "78502ce569750cb26ae1e50f1fe708653cede06c";
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
		ownerAccountCapsule.setStakeSupernode(Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT);
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
						.setSupernodeAddress(ByteString.copyFrom(ByteArray.fromHexString(supernodeAddress)))
						.build());
	}

	/**
	 * Update witness,result is success.
	 */
	@Test
	public void rightResignWitness() {
		WitnessResignActuator actuator = new WitnessResignActuator(getContract(OWNER_ADDRESS, SUPERNODE_ADDRESS),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			Assert.assertFalse(dbManager.getWitnessStore().has(ByteArray.fromHexString(SUPERNODE_ADDRESS)));

			AccountCapsule ownerAccountCapsule = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(ownerAccountCapsule.getTotalStakeAmount(), ownerAccountCapsule.getNormalStakeAmount());
			Assert.assertEquals(0, ownerAccountCapsule.getSupernodeStakeAmount());

			long temp = ownerAccountCapsule.getStake().getExpireTime();
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
			Assert.fail("Invalid supernodeAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid supernodeAddress", e.getMessage());
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
			Assert.fail("Supernode account does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Supernode account does not exist", e.getMessage());
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
			Assert.fail("Witness does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Address " + OTHER_OWNER_ADDRESS +
					" does not own supernode " + SUPERNODE_ADDRESS, e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}