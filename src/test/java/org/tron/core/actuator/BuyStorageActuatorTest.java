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
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.util.ConversionUtil;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;

@Slf4j
public class BuyStorageActuatorTest {

	private static final String dbPath = "output_buy_storage_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(10_000_000_000L);
	private static Manager dbManager;
	private static TronApplicationContext context;

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
	public void createAccountCapsule() {
		AccountCapsule ownerCapsule =
				new AccountCapsule(
						ByteString.copyFromUtf8("owner"),
						ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
						AccountType.Normal,
						initBalance);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		dbManager.getDynamicPropertiesStore().saveTotalStorageReserved(
				128L * 1024 * 1024 * 1024);
		dbManager.getDynamicPropertiesStore().saveTotalStoragePool(
				ConversionUtil.McashToMatoshi(100_000_000));
		dbManager.getDynamicPropertiesStore().saveTotalStorageTax(0);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);
	}

	private Any getContract(String ownerAddress, long quant) {
		return Any.pack(
				Contract.BuyStorageContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
						.setQuant(quant)
						.build());
	}

	@Test
	public void testBuyStorage() {
		long currentPool = dbManager.getDynamicPropertiesStore().getTotalStoragePool();
		long currentReserved = dbManager.getDynamicPropertiesStore().getTotalStorageReserved();
		Assert.assertEquals(currentPool, ConversionUtil.McashToMatoshi(100_000_000));
		Assert.assertEquals(currentReserved, 128L * 1024 * 1024 * 1024);

		long quant = ConversionUtil.McashToMatoshi(2_000_000); // 2 million trx
		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance - quant
					- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(2694881440L, owner.getStorageLimit());
			Assert.assertEquals(currentReserved - 2694881440L,
					dbManager.getDynamicPropertiesStore().getTotalStorageReserved());
			Assert.assertEquals(currentPool + quant,
					dbManager.getDynamicPropertiesStore().getTotalStoragePool());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testBuyStorage2() {
		long currentPool = dbManager.getDynamicPropertiesStore().getTotalStoragePool();
		long currentReserved = dbManager.getDynamicPropertiesStore().getTotalStorageReserved();
		Assert.assertEquals(currentPool, ConversionUtil.McashToMatoshi(100_000_000));
		Assert.assertEquals(currentReserved, 128L * 1024 * 1024 * 1024);

		long quant = ConversionUtil.McashToMatoshi(1_000_000); // 1 million trx

		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		BuyStorageActuator actuator2 = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret2 = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), initBalance - quant
					- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(1360781717L, owner.getStorageLimit());
			Assert.assertEquals(currentReserved - 1360781717L,
					dbManager.getDynamicPropertiesStore().getTotalStorageReserved());
			Assert.assertEquals(currentPool + quant,
					dbManager.getDynamicPropertiesStore().getTotalStoragePool());

			actuator2.validate();
			actuator2.execute(ret2);
			Assert.assertEquals(ret2.getInstance().getRet(), code.SUCCESS);

			owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), initBalance - 2 * quant
					- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(2694881439L, owner.getStorageLimit());
			Assert.assertEquals(currentReserved - 2694881439L,
					dbManager.getDynamicPropertiesStore().getTotalStorageReserved());
			Assert.assertEquals(currentPool + 2 * quant,
					dbManager.getDynamicPropertiesStore().getTotalStoragePool());

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void buyLessThanZero() {
		long quant = -1_000_000_000L;
		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Quantity must be positive");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Quantity must be positive", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void buyLessThan1Mcash() {
		long quant = ConversionUtil.McashToMatoshi(0.2);
		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Quantity must be larger than 1 MCASH");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Quantity must be larger than 1 MCASH", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void buyLessThan1Byte() {
		long currentPool = dbManager.getDynamicPropertiesStore().getTotalStoragePool();
		long currentReserved = dbManager.getDynamicPropertiesStore().getTotalStorageReserved();
		Assert.assertEquals(currentPool, ConversionUtil.McashToMatoshi(100_000_000));
		Assert.assertEquals(currentReserved, 128L * 1024 * 1024 * 1024);

		long quant = ConversionUtil.McashToMatoshi(9_000_000_000L);

		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		BuyStorageActuator actuator2 = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, ConversionUtil.McashToMatoshi(1)), dbManager);
		TransactionResultCapsule ret2 = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(owner.getBalance(), initBalance - quant
					- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(135928635301L, owner.getStorageLimit());
			Assert.assertEquals(currentReserved - 135928635301L,
					dbManager.getDynamicPropertiesStore().getTotalStorageReserved());
			Assert.assertEquals(currentPool + quant,
					dbManager.getDynamicPropertiesStore().getTotalStoragePool());

			actuator2.validate();
			actuator2.execute(ret2);
			Assert.fail("storageBytes must be larger than 1, current storageBytes 0");

		} catch (ContractValidateException e) {
			Assert.assertEquals("storageBytes must be larger than 1, current storageBytes 0",
					e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	@Test
	public void buyMoreThanBalance() {
		long quant = ConversionUtil.McashToMatoshi(11_000_000_000L);
		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Quantity must be less than accountBalance");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Quantity must be less than accountBalance", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		long quant = ConversionUtil.McashToMatoshi(1000);
		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ADDRESS_INVALID, quant), dbManager);
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
	public void invalidOwnerAccount() {
		long quant = ConversionUtil.McashToMatoshi(1000);
		BuyStorageActuator actuator = new BuyStorageActuator(
				getContract(OWNER_ACCOUNT_INVALID, quant), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + OWNER_ACCOUNT_INVALID + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ACCOUNT_INVALID + " does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}
