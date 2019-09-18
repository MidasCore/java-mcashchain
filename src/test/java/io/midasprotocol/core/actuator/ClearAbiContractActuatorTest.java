package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.runtime.config.VMConfig;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.Wallet;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testng.Assert;

import java.io.File;

import static io.midasprotocol.utils.ContractUtil.jsonStr2Abi;

@Slf4j
public class ClearAbiContractActuatorTest {

	private static ApplicationContext context;
	private static Manager dbManager;
	private static final String dbPath = "output_clearabicontract_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
	private static final String SECOND_ACCOUNT_ADDRESS;
	private static final String OWNER_ADDRESS_NOTEXIST;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String SMART_CONTRACT_NAME = "smart_contarct";
	private static final String CONTRACT_ADDRESS = "111111";
	private static final String NO_EXIST_CONTRACT_ADDRESS = "2222222";
	private static final Protocol.SmartContract.ABI SOURCE_ABI = jsonStr2Abi("[{\"inputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]");
	private static final Protocol.SmartContract.ABI TARGET_ABI = Protocol.SmartContract.ABI.getDefaultInstance();

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
		VMConfig.initAllowVmConstantinople(1);
		dbManager = context.getBean(Manager.class);
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
		builder.setAbi(SOURCE_ABI);
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

	private Any getContract(String accountAddress, String contractAddress) {
		return Any.pack(
			Contract.ClearAbiContract.newBuilder()
				.setOwnerAddress(StringUtil.hexString2ByteString(accountAddress))
				.setContractAddress(StringUtil.hexString2ByteString(contractAddress))
				.build());
	}

	@Test
	public void successClearAbiContract() {
		ClearAbiContractActuator actuator =
			new ClearAbiContractActuator(
				getContract(OWNER_ADDRESS, CONTRACT_ADDRESS), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			// assert result state and consume_user_resource_percent
			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			Assert.assertEquals(
				dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS)).
					getInstance().getAbi(),
				TARGET_ABI);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail();
		}
	}

	@Test
	public void invalidAddress() {
		ClearAbiContractActuator actuator =
			new ClearAbiContractActuator(getContract(OWNER_ADDRESS_INVALID, CONTRACT_ADDRESS), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Invalid address");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail();
		}
	}

	@Test
	public void notExistAccount() {
		ClearAbiContractActuator actuator =
			new ClearAbiContractActuator(getContract(OWNER_ADDRESS_NOTEXIST, CONTRACT_ADDRESS), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Account " + OWNER_ADDRESS_NOTEXIST + " not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ADDRESS_NOTEXIST + " not exists", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail();
		}
	}

	@Test
	public void notExistContract() {
		ClearAbiContractActuator actuator =
			new ClearAbiContractActuator(getContract(OWNER_ADDRESS, NO_EXIST_CONTRACT_ADDRESS), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			Assert.fail("Contract not exists");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Contract not exists", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail();
		}
	}

	@Test
	public void callerNotContractOwner() {
		ClearAbiContractActuator actuator =
			new ClearAbiContractActuator(getContract(SECOND_ACCOUNT_ADDRESS, CONTRACT_ADDRESS), dbManager);

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
			Assert.fail();
		}
	}

	@Test
	public void twiceClearAbi() {
		ClearAbiContractActuator actuator =
			new ClearAbiContractActuator(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS), dbManager);

		ClearAbiContractActuator secondActuator =
			new ClearAbiContractActuator(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS), dbManager);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			// first
			actuator.validate();
			actuator.execute(ret);

			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			Assert.assertEquals(
				dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS)).
					getInstance().getAbi(),
				TARGET_ABI);

			// second
			secondActuator.validate();
			secondActuator.execute(ret);

			Assert.assertEquals(ret.getInstance().getCode(), Protocol.Transaction.Result.Code.SUCCESS);
			Assert.assertEquals(
				dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS)).
					getInstance().getAbi(),
				TARGET_ABI);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail();
		}
	}
}