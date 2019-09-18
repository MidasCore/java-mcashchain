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
import io.midasprotocol.core.capsule.DelegatedResourceAccountIndexCapsule;
import io.midasprotocol.core.capsule.DelegatedResourceCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter.ChainConstant;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.ResourceCode;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class FreezeBalanceActuatorTest {

	private static final String dbPath = "output_freeze_balance_test";
	private static final String OWNER_ADDRESS;
	private static final String RECEIVER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(1_000_000);
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		RECEIVER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
		OWNER_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
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

		AccountCapsule receiverCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("receiver"),
				ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)),
				AccountType.Normal,
				initBalance);
		dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);
	}

	private Any getContractForBandwidth(String ownerAddress, long frozenBalance, long duration) {
		return Any.pack(
			Contract.FreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setFrozenBalance(frozenBalance)
				.setFrozenDuration(duration)
				.build());
	}

	private Any getContractForEnergy(String ownerAddress, long frozenBalance, long duration) {
		return Any.pack(
			Contract.FreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setFrozenBalance(frozenBalance)
				.setFrozenDuration(duration)
				.setResource(ResourceCode.ENERGY)
				.build());
	}

	private Any getDelegatedContractForBandwidth(String ownerAddress, String receiverAddress,
												 long frozenBalance,
												 long duration) {
		return Any.pack(
			Contract.FreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
				.setFrozenBalance(frozenBalance)
				.setFrozenDuration(duration)
				.build());
	}

	private Any getDelegatedContractForEnergy(String ownerAddress, String receiverAddress,
											  long frozenBalance,
											  long duration) {
		return Any.pack(
			Contract.FreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
				.setFrozenBalance(frozenBalance)
				.setFrozenDuration(duration)
				.setResource(ResourceCode.ENERGY)
				.build());
	}

	@Test
	public void freezeBalanceForBandwidth() {
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
				- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(owner.getFrozenBalanceForBandwidth(), frozenBalance);
			Assert.assertEquals(0, owner.getVotingPower());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void freezeBalanceForEnergy() {
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForEnergy(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
				- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(0L, owner.getFrozenBalanceForBandwidth());
			Assert.assertEquals(frozenBalance, owner.getFrozenBalanceForEnergy());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void freezeDelegatedBalanceForBandwidth() {
		dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS, frozenBalance, duration),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long totalNetWeightBefore = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
				- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(0L, owner.getFrozenBalanceForBandwidth());
			Assert.assertEquals(frozenBalance, owner.getDelegatedFrozenBalanceForBandwidth());

			AccountCapsule receiver =
				dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
			Assert.assertEquals(frozenBalance, receiver.getAcquiredDelegatedFrozenBalanceForBandwidth());
			Assert.assertEquals(0L, receiver.getAcquiredDelegatedFrozenBalanceForEnergy());
			Assert.assertEquals(0L, receiver.getVotingPower());

			DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
				.get(DelegatedResourceCapsule
					.createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
						ByteArray.fromHexString(RECEIVER_ADDRESS)));

			Assert.assertEquals(frozenBalance, delegatedResourceCapsule.getFrozenBalanceForBandwidth());
			long totalNetWeightAfter = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();
			Assert.assertEquals(totalNetWeightBefore + frozenBalance / ChainConstant.TEN_POW_DECIMALS, totalNetWeightAfter);

			//check DelegatedResourceAccountIndex
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
				.getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
			Assert.assertEquals(1, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());
			Assert.assertTrue(delegatedResourceAccountIndexCapsuleOwner.getToAccountsList()
				.contains(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS))));

			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
				.getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
			Assert.assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
			Assert.assertEquals(1,
				delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());
			Assert.assertTrue(delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList()
				.contains(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS))));

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void freezeDelegatedBalanceForEnergy() {
		dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getDelegatedContractForEnergy(OWNER_ADDRESS, RECEIVER_ADDRESS, frozenBalance, duration),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		long totalEnergyWeightBefore = dbManager.getDynamicPropertiesStore().getTotalEnergyWeight();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
				- ChainConstant.TRANSFER_FEE);
			Assert.assertEquals(0L, owner.getFrozenBalanceForBandwidth());
			Assert.assertEquals(0L, owner.getDelegatedFrozenBalanceForBandwidth());
			Assert.assertEquals(frozenBalance, owner.getDelegatedFrozenBalanceForEnergy());

			AccountCapsule receiver =
				dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
			Assert.assertEquals(0L, receiver.getAcquiredDelegatedFrozenBalanceForBandwidth());
			Assert.assertEquals(frozenBalance, receiver.getAcquiredDelegatedFrozenBalanceForEnergy());
			Assert.assertEquals(0L, receiver.getVotingPower());

			DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
				.get(DelegatedResourceCapsule
					.createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
						ByteArray.fromHexString(RECEIVER_ADDRESS)));

			Assert.assertEquals(0L, delegatedResourceCapsule.getFrozenBalanceForBandwidth());
			Assert.assertEquals(frozenBalance, delegatedResourceCapsule.getFrozenBalanceForEnergy());

			long totalEnergyWeightAfter = dbManager.getDynamicPropertiesStore().getTotalEnergyWeight();
			Assert.assertEquals(totalEnergyWeightBefore + frozenBalance / ChainConstant.TEN_POW_DECIMALS,
				totalEnergyWeightAfter);

			//check DelegatedResourceAccountIndex
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
				.getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert
				.assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
			Assert.assertEquals(1, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());
			Assert.assertTrue(delegatedResourceAccountIndexCapsuleOwner.getToAccountsList()
				.contains(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS))));

			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
				.getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
			Assert.assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
			Assert.assertEquals(1,
				delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());
			Assert.assertTrue(delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList()
				.contains(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS))));

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void freezeLessThanZero() {
		long frozenBalance = ConversionUtil.McashToMatoshi(-1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("frozenBalance must be positive", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void freezeMoreThanBalance() {
		long frozenBalance = ConversionUtil.McashToMatoshi(11_000_000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("frozenBalance must be less than accountBalance", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS_INVALID, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	@Test
	public void invalidOwnerAccount() {
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ACCOUNT_INVALID, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + OWNER_ACCOUNT_INVALID + " does not exist",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void durationLessThanMin() {
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 2;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");

		} catch (ContractValidateException e) {
			long minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
			long maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();
			Assert.assertEquals("frozenDuration must be less than " + maxFrozenTime + " days "
					+ "and more than " + minFrozenTime + " days"
				, e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void durationMoreThanMax() {
		long frozenBalance = ConversionUtil.McashToMatoshi(1000);
		long duration = 4;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("cannot run here.");
		} catch (ContractValidateException e) {
			long minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
			long maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();
			Assert.assertEquals("frozenDuration must be less than " + maxFrozenTime + " days "
					+ "and more than " + minFrozenTime + " days"
				, e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void lessThan1McashTest() {
		long frozenBalance = 1;
		long duration = 3;
		FreezeBalanceActuator actuator = new FreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS, frozenBalance, duration), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("frozenBalance must be more than 1 MCASH", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}
}
