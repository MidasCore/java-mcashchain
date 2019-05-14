package org.tron.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.File;

import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.util.ConversionUtil;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j

public class WitnessCreateActuatorTest {

	private static TronApplicationContext context;
	private static Manager dbManager;
	private static final String dbPath = "output_WitnessCreate_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String SUPERNODE_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String SUPERNODE_ADDRESS_SECOND;
	private static final String ACCOUNT_NAME_THIRD = "ownerM";
	private static final String SUPERNODE_ADDRESS_THIRD;
	private static final String OWNER_ADDRESS;
	private static final String URL = "https://tron.network";
	private static final String SUPERNODE_ADDRESS_INVALID = "aaaa";
	private static final String SUPERNODE_ADDRESS_NOACCOUNT;
	private static final String OWNER_ADDRESS_BALANCENOTSUFFIENT;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS_FIRST = "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_SECOND = "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_THIRD = "2b0c293ff59d17813b11161999b367e012173bd3";
		SUPERNODE_ADDRESS_NOACCOUNT = "548794500882809695a8a687866e76d4271a1aed";
		OWNER_ADDRESS_BALANCENOTSUFFIENT = "548794500882809695a8a687866e06d4271a1ced";
		OWNER_ADDRESS = "84ca19269c61f4778e51a8ed085620d7ac1fc2ea";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);

	}

	/**
	 * create temp Capsule test need.
	 */
	@Before
	public void createCapsule() {
		WitnessCapsule ownerCapsule =
				new WitnessCapsule(
						ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND)),
						ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
						10_000_000L,
						URL);
		AccountCapsule ownerAccountCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8(""),
						ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
						AccountType.Normal,
						ConversionUtil.McashToMatoshi(200_000));
		ownerAccountCapsule.setStake(1000000000000000L, 0);

		AccountCapsule supernodeAccountSecondCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
						ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND)),
						AccountType.Normal,
						0);
		AccountCapsule supernodeAccountFirstCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8(ACCOUNT_NAME_FIRST),
						ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)),
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
		dbManager.getWitnessStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST));
		dbManager.getWitnessStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_THIRD));
	}

	private Any getContract(String address, String ownerAddress, String url) {
		return Any.pack(
				Contract.WitnessCreateContract.newBuilder()
						.setSupernodeAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
						.setUrl(ByteString.copyFrom(ByteArray.fromString(url)))
						.build());
	}

	private Any getContract(String address, String ownerAddress, ByteString url) {
		return Any.pack(
				Contract.WitnessCreateContract.newBuilder()
						.setSupernodeAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
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
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			WitnessCapsule witnessCapsule =
					dbManager.getWitnessStore().get(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(
					witnessCapsule.getInstance().getUrl(),
					URL);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * second createWitness,result is failed,exception is "Witness has existed".
	 */
	@Test
	public void secondCreateAccount() {
		WitnessCreateActuator actuator =
				new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_SECOND, OWNER_ADDRESS, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + SUPERNODE_ADDRESS_SECOND + " has existed");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness " + SUPERNODE_ADDRESS_SECOND + " has existed", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 *
	 */
	@Test
	public void create2MoreWitnesses() {
		WitnessCreateActuator actuator =
				new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			WitnessCapsule witnessCapsule =
					dbManager.getWitnessStore().get(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(
					witnessCapsule.getInstance().getUrl(),
					URL);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
		actuator = new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_THIRD, OWNER_ADDRESS, URL), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Owner stake amount < required stake amount 5000000 MCASH");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Owner stake amount < required stake amount 5000000 MCASH", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Invalid Address createWitness,result is failed,exception is "Invalid address".
	 */
	@Test
	public void InvalidAddress() {
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
	public void InvalidUrlTest() {
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

		// 1 byte url is ok.
		try {
			WitnessCreateActuator actuator = new WitnessCreateActuator(
					getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS, "0"), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			WitnessCapsule witnessCapsule =
					dbManager.getWitnessStore().get(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(witnessCapsule.getInstance().getUrl(), "0");
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		dbManager.getWitnessStore().delete(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST));
		// 256 bytes url is ok.
		try {
			WitnessCreateActuator actuator = new WitnessCreateActuator(
					getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS, url256Bytes), dbManager);
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			WitnessCapsule witnessCapsule =
					dbManager.getWitnessStore().get(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST));
			Assert.assertNotNull(witnessCapsule);
			Assert.assertEquals(witnessCapsule.getInstance().getUrl(), url256Bytes);
			Assert.assertTrue(true);
		} catch (ContractValidateException | ContractExeException e) {
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
				new WitnessCreateActuator(getContract(SUPERNODE_ADDRESS_FIRST, OWNER_ADDRESS_BALANCENOTSUFFIENT, URL), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("witnessAccount  has balance " + balanceNotSufficientCapsule.getBalance()
					+ " < MIN_BALANCE 100");
		} catch (ContractValidateException e) {
			Assert.assertEquals("balance < AccountUpgradeCost", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
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
}