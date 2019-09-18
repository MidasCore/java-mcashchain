package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ContractCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;

import java.io.File;


@Slf4j
public class UpdateSettingContractActuatorTest {

	private static final String dbPath = "output_update_setting_contract_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
	private static final String SECOND_ACCOUNT_ADDRESS;
	private static final String OWNER_ADDRESS_NOTEXIST;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String SMART_CONTRACT_NAME = "smart_contarct";
	private static final String CONTRACT_ADDRESS = "111111";
	private static final String NO_EXIST_CONTRACT_ADDRESS = "2222222";
	private static final long SOURCE_PERCENT = 10L;
	private static final long TARGET_PERCENT = 30L;
	private static final long INVALID_PERCENT = 200L;
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		OWNER_ADDRESS_NOTEXIST = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		SECOND_ACCOUNT_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
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
		// address in accountStore and the owner of contract
		AccountCapsule accountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule);

		// smartContract in contractStore
		Protocol.SmartContract.Builder builder = Protocol.SmartContract.newBuilder();
		builder.setName(SMART_CONTRACT_NAME);
		builder.setOriginAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
		builder.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_ADDRESS)));
		builder.setConsumeUserResourcePercent(SOURCE_PERCENT);
		dbManager.getContractStore().put(
			ByteArray.fromHexString(CONTRACT_ADDRESS),
			new ContractCapsule(builder.build()));

		// address in accountStore not the owner of contract
		AccountCapsule secondAccount =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS)),
				ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
				Protocol.AccountType.Normal);
		dbManager.getAccountStore().put(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS), secondAccount);

		// address does not exist in accountStore
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOTEXIST));
	}

	private Any getContract(String accountAddress, String contractAddress, long percent) {
		return Any.pack(
			Contract.UpdateSettingContract.newBuilder()
				.setOwnerAddress(StringUtil.hexString2ByteString(accountAddress))
				.setContractAddress(StringUtil.hexString2ByteString(contractAddress))
				.setConsumeUserResourcePercent(percent).build());
	}

	@Test
	public void successUpdateSettingContract() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_PERCENT), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			// assert result state and consume_user_resource_percent
			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			Assert.assertEquals(
				dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS)).
					getConsumeUserResourcePercent(),
				TARGET_PERCENT);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidAddress() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS_INVALID, CONTRACT_ADDRESS, TARGET_PERCENT), dbManager);

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
	public void noExistAccount() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS_NOTEXIST, CONTRACT_ADDRESS, TARGET_PERCENT), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Account " + OWNER_ADDRESS_NOTEXIST + " does not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ADDRESS_NOTEXIST + " does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidResourcePercent() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, INVALID_PERCENT), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Percent not in [0, 100]");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Percent not in [0, 100]", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noExistContract() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS, NO_EXIST_CONTRACT_ADDRESS, TARGET_PERCENT), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Contract does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Contract does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void callerNotContractOwner() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(SECOND_ACCOUNT_ADDRESS, CONTRACT_ADDRESS, TARGET_PERCENT), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Account " + SECOND_ACCOUNT_ADDRESS + " is not the owner of the contract");
		} catch (ContractValidateException e) {
			Assert.assertEquals(
				"Account " + SECOND_ACCOUNT_ADDRESS + " is not the owner of the contract",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void twiceUpdateSettingContract() {
		UpdateSettingContractActuator actuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_PERCENT), dbManager);

		UpdateSettingContractActuator secondActuator =
			new UpdateSettingContractActuator(
				getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, 90L), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			// first
			actuator.validate();
			actuator.execute(ret);

			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			Assert.assertEquals(
				dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS)).
					getConsumeUserResourcePercent(),
				TARGET_PERCENT);

			// second
			secondActuator.validate();
			secondActuator.execute(ret);

			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			Assert.assertEquals(
				dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS)).
					getConsumeUserResourcePercent(),
				90L);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}
