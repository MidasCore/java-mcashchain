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
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class CreateAccountActuatorTest {

	private static final String dbPath = "output_create_account_test";
	private static final String OWNER_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String OWNER_ADDRESS_SECOND;
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS_FIRST = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		OWNER_ADDRESS_SECOND = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
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
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				AccountType.AssetIssue);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_FIRST));
	}

	private Any getContract(String ownerAddress, String accountAddress) {
		return Any.pack(
			Contract.AccountCreateContract.newBuilder()
				.setAccountAddress(ByteString.copyFrom(ByteArray.fromHexString(accountAddress)))
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.build());
	}

	/**
	 * Unit test.
	 */
	@Test
	public void firstCreateAccount() {
		CreateAccountActuator actuator =
			new CreateAccountActuator(getContract(OWNER_ADDRESS_SECOND, OWNER_ADDRESS_FIRST),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule accountCapsule =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS_FIRST));
			Assert.assertNotNull(accountCapsule);
			Assert.assertEquals(
				StringUtil.createReadableString(accountCapsule.getAddress()),
				OWNER_ADDRESS_FIRST);
		} catch (ContractValidateException e) {
			logger.info(e.getMessage());
			Assert.fail(e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Unit test.
	 */
	@Test
	public void secondCreateAccount() {
		CreateAccountActuator actuator =
			new CreateAccountActuator(
				getContract(OWNER_ADDRESS_SECOND, OWNER_ADDRESS_SECOND), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
		} catch (ContractValidateException e) {
			AccountCapsule accountCapsule =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS_SECOND));
			Assert.assertNotNull(accountCapsule);
			Assert.assertEquals(
				accountCapsule.getAddress(),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}
