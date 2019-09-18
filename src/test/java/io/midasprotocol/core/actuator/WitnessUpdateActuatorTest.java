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
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class WitnessUpdateActuatorTest {

	private static final String dbPath = "output_witness_update_test";
	private static final String WITNESS_ADDRESS;
	private static final String WITNESS_ADDRESS_ACCOUNT_NAME = "test_account";
	private static final String ADDRESS_NOT_WITNESS;
	private static final String SUPERNODE_ADDRESS_NOT_WITNESS_ACCOUNT_NAME = "test_account1";
	private static final String WITNESS_ADDRESS_NOTEXIST;
	private static final String OWNER_ADDRESS;
	private static final String URL = "https://mcash.network";
	private static final String NewURL = "https://mcash.org";
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		WITNESS_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		WITNESS_ADDRESS_NOTEXIST = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		ADDRESS_NOT_WITNESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "4536f33f3e0a725484738017c533f877d5df3a82";
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
		AccountCapsule ownerAccountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				ByteString.copyFromUtf8(WITNESS_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(ownerAccountCapsule.createDbKey(), ownerAccountCapsule);

		// address in accountStore and witnessStore
		AccountCapsule accountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(WITNESS_ADDRESS)),
				ByteString.copyFromUtf8(WITNESS_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		WitnessCapsule witnessCapsule = new WitnessCapsule(
			ByteString.copyFrom(ByteArray.fromHexString(WITNESS_ADDRESS)),
			ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
			1_000_000_000L, URL);
		dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);

		// address exist in accountStore, but is not witness
		AccountCapsule accountNotWitnessCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(ADDRESS_NOT_WITNESS)),
				ByteString.copyFromUtf8(SUPERNODE_ADDRESS_NOT_WITNESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore()
			.put(ByteArray.fromHexString(ADDRESS_NOT_WITNESS), accountNotWitnessCapsule);
		dbManager.getWitnessStore().delete(ByteArray.fromHexString(ADDRESS_NOT_WITNESS));

		// address does not exist in accountStore
		dbManager.getAccountStore().delete(ByteArray.fromHexString(WITNESS_ADDRESS_NOTEXIST));
	}

	private Any getContract(String address, String supernodeAddress, String url) {
		return Any.pack(
			Contract.WitnessUpdateContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(supernodeAddress)))
				.setUpdateUrl(ByteString.copyFrom(ByteArray.fromString(url)))
				.build());
	}

	private Any getContract(String address, String supernodeAddress, ByteString url) {
		return Any.pack(
			Contract.WitnessUpdateContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setWitnessAddress(ByteString.copyFrom(ByteArray.fromHexString(supernodeAddress)))
				.setUpdateUrl(url)
				.build());
	}

	/**
	 * Update witness,result is success.
	 */
	@Test
	public void rightUpdateWitness() {
		WitnessUpdateActuator actuator = new WitnessUpdateActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, NewURL),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
				.get(ByteArray.fromHexString(WITNESS_ADDRESS));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(witnessCapsule.getUrl(), NewURL);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Invalid Address update witness,result is failed,exception is "Invalid address".
	 */
	@Test
	public void InvalidAddress() {
		WitnessUpdateActuator actuator = new WitnessUpdateActuator(
			getContract(OWNER_ADDRESS_INVALID, WITNESS_ADDRESS, NewURL), dbManager);
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

	/**
	 * use Invalid url createWitness,result is failed,exception is "Invalid url".
	 */
	@Test
	public void InvalidUrlTest() {
		TransactionResultCapsule ret = new TransactionResultCapsule();
		//Url cannot empty
		try {
			WitnessUpdateActuator actuator = new WitnessUpdateActuator(
				getContract(OWNER_ADDRESS, WITNESS_ADDRESS, ByteString.EMPTY), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		//256 bytes
		String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" +
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" +
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		//Url length can not greater than 256
		try {
			WitnessUpdateActuator actuator = new WitnessUpdateActuator(
				getContract(OWNER_ADDRESS, WITNESS_ADDRESS, ByteString.copyFromUtf8(url256Bytes + "0")), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid url");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid url", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// 1 byte url is ok.
		try {
			WitnessUpdateActuator actuator = new WitnessUpdateActuator(
				getContract(OWNER_ADDRESS, WITNESS_ADDRESS, "0"),
				dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
				.get(ByteArray.fromHexString(WITNESS_ADDRESS));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(witnessCapsule.getUrl(), "0");
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// 256 bytes url is ok.
		try {
			WitnessUpdateActuator actuator = new WitnessUpdateActuator(
				getContract(OWNER_ADDRESS, WITNESS_ADDRESS, url256Bytes), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
				.get(ByteArray.fromHexString(WITNESS_ADDRESS));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(witnessCapsule.getUrl(), url256Bytes);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use AccountStore not exists Address createWitness,result is failed,exception is "Witness does
	 * not exist"
	 */
	@Test
	public void notExistWitness() {
		WitnessUpdateActuator actuator = new WitnessUpdateActuator(
			getContract(OWNER_ADDRESS, ADDRESS_NOT_WITNESS, URL), dbManager);
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

	/**
	 * if account does not exist in accountStore, the test will throw a Exception
	 */
	@Test
	public void notExistAccount() {
		WitnessUpdateActuator actuator = new WitnessUpdateActuator(
			getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOTEXIST, URL), dbManager);
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
}