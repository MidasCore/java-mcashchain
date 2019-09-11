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
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class UnfreezeBalanceActuatorTest {

	private static final String dbPath = "output_unfreeze_balance_test";
	private static final String OWNER_ADDRESS;
	private static final String RECEIVER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(10_000);
	private static final long frozenBalance = ConversionUtil.McashToMatoshi(1_000);
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
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);

		AccountCapsule receiverCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("receiver"),
				ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)),
				AccountType.Normal,
				initBalance);
		dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);
	}

	private Any getContractForBandwidth(String ownerAddress) {
		return Any.pack(
			Contract.UnfreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.build());
	}

	private Any getContractForEnergy(String ownerAddress) {
		return Any.pack(
			Contract.UnfreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setResource(io.midasprotocol.protos.Contract.ResourceCode.ENERGY)
				.build());
	}

	private Any getDelegatedContractForBandwidth(String ownerAddress, String receiverAddress) {
		return Any.pack(
			Contract.UnfreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
				.build());
	}

	private Any getDelegatedContractForEnergy(String ownerAddress, String receiverAddress) {
		return Any.pack(
			Contract.UnfreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
				.setResource(io.midasprotocol.protos.Contract.ResourceCode.ENERGY)
				.build());
	}

	private Any getContract(String ownerAddress, Contract.ResourceCode resourceCode) {
		return Any.pack(
			Contract.UnfreezeBalanceContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
				.setResource(resourceCode)
				.build());
	}


	@Test
	public void unfreezeBalanceForBandwidth() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setFrozenForBandwidth(frozenBalance, now);
		Assert.assertEquals(accountCapsule.getFrozenBalanceForBandwidth(), frozenBalance);

		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		long totalNetWeightBefore = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance + frozenBalance);
			Assert.assertEquals(owner.getFrozenBalanceForBandwidth(), 0);

			long totalNetWeightAfter = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();
			Assert.assertEquals(totalNetWeightBefore,
				totalNetWeightAfter + frozenBalance / Parameter.ChainConstant.TEN_POW_DECIMALS);

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}


	@Test
	public void unfreezeBalanceForEnergy() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setFrozenForEnergy(frozenBalance, now);
		Assert.assertEquals(accountCapsule.getAllFrozenBalanceForEnergy(), frozenBalance);

		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getContractForEnergy(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		long totalEnergyWeightBefore = dbManager.getDynamicPropertiesStore().getTotalEnergyWeight();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			Assert.assertEquals(owner.getBalance(), initBalance + frozenBalance);
			Assert.assertEquals(owner.getFrozenBalanceForEnergy(), 0);
			long totalEnergyWeightAfter = dbManager.getDynamicPropertiesStore().getTotalEnergyWeight();
			Assert.assertEquals(totalEnergyWeightBefore,
				totalEnergyWeightAfter + frozenBalance / Parameter.ChainConstant.TEN_POW_DECIMALS);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void unfreezeDelegatedBalanceForBandwidth() {
		dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule owner = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setDelegatedFrozenBalanceForBandwidth(frozenBalance);

		AccountCapsule receiver = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(RECEIVER_ADDRESS));
		receiver.setAcquiredDelegatedFrozenBalanceForBandwidth(frozenBalance);

		dbManager.getAccountStore().put(owner.createDbKey(), owner);
		dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

		//init DelegatedResourceCapsule
		DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
			owner.getAddress(),
			receiver.getAddress()
		);
		delegatedResourceCapsule.setFrozenBalanceForBandwidth(
			frozenBalance,
			now - 100L);
		dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
			.createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
				ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

		//init DelegatedResourceAccountIndex
		{
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
				new DelegatedResourceAccountIndexCapsule(owner.getAddress());
			delegatedResourceAccountIndex
				.addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
			dbManager.getDelegatedResourceAccountIndexStore()
				.put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
		}

		{
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
				new DelegatedResourceAccountIndexCapsule(receiver.getAddress());
			delegatedResourceAccountIndex
				.addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
			dbManager.getDelegatedResourceAccountIndexStore()
				.put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
		}

		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule ownerResult =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			AccountCapsule receiverResult =
				dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));

			Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
			Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForBandwidth());
			Assert.assertEquals(0L, receiverResult.getAllFrozenBalanceForBandwidth());

			//check DelegatedResourceAccountIndex
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
				.getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
			Assert.assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
				.getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
			Assert.assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
			Assert.assertEquals(0,
				delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void unfreezeDelegatedBalanceForEnergy() {
		dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule owner = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.addDelegatedFrozenBalanceForEnergy(frozenBalance);

		AccountCapsule receiver = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(RECEIVER_ADDRESS));
		receiver.addAcquiredDelegatedFrozenBalanceForEnergy(frozenBalance);

		dbManager.getAccountStore().put(owner.createDbKey(), owner);
		dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

		DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
			owner.getAddress(),
			receiver.getAddress()
		);
		delegatedResourceCapsule.setFrozenBalanceForEnergy(
			frozenBalance,
			now - 100L);
		dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
			.createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
				ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getDelegatedContractForEnergy(OWNER_ADDRESS, RECEIVER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();

		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule ownerResult =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

			AccountCapsule receiverResult =
				dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));

			Assert.assertEquals(initBalance + frozenBalance, ownerResult.getBalance());
			Assert.assertEquals(0L, ownerResult.getDelegatedFrozenBalanceForEnergy());
			Assert.assertEquals(0L, receiverResult.getAllFrozenBalanceForEnergy());
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setFrozenForBandwidth(frozenBalance, now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	@Test
	public void invalidOwnerAccount() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setFrozenForBandwidth(frozenBalance, now);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getContractForBandwidth(OWNER_ACCOUNT_INVALID), dbManager);
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

	@Test
	public void noFrozenBalance() {
		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");

		} catch (ContractValidateException e) {
			Assert.assertEquals("No frozenBalance (Bandwidth)", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notTimeToUnfreeze() {
		long now = System.currentTimeMillis();
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		accountCapsule.setFrozenForBandwidth(frozenBalance, now + 60000);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
			getContractForBandwidth(OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot run here.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("It's not time to unfreeze (Bandwidth).", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

//  @Test
//  public void InvalidTotalNetWeight(){
//    long now = System.currentTimeMillis();
//    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
//    dbManager.getDynamicPropertiesStore().saveTotalBandwidthWeight(smallTatalResource);
//
//    AccountCapsule accountCapsule = dbManager.getAccountStore()
//            .get(ByteArray.fromHexString(OWNER_ADDRESS));
//    accountCapsule.setFrozenForBandwidth(frozenBalance, now);
//    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
//
//    Assert.assertTrue(frozenBalance/1000_000L > smallTatalResource );
//    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
//            getContract(OWNER_ADDRESS), dbManager);
//    TransactionResultCapsule ret = new TransactionResultCapsule();
//    try {
//      actuator.validate();
//      actuator.execute(ret);
//
//      Assert.assertTrue(dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight() >= 0);
//    } catch (ContractValidateException e) {
//      Assert.assertTrue(e instanceof ContractValidateException);
//    } catch (ContractExeException e) {
//      Assert.assertTrue(e instanceof ContractExeException);
//    }
//  }
//
//  @Test
//  public void InvalidTotalEnergyWeight(){
//    long now = System.currentTimeMillis();
//    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
//    dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(smallTatalResource);
//
//    AccountCapsule accountCapsule = dbManager.getAccountStore()
//            .get(ByteArray.fromHexString(OWNER_ADDRESS));
//    accountCapsule.setFrozenForEnergy(frozenBalance, now);
//    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
//
//    Assert.assertTrue(frozenBalance/1000_000L > smallTatalResource );
//    UnfreezeBalanceActuator actuator = new UnfreezeBalanceActuator(
//            getContract(OWNER_ADDRESS, Contract.ResourceCode.ENERGY), dbManager);
//    TransactionResultCapsule ret = new TransactionResultCapsule();
//    try {
//      actuator.validate();
//      actuator.execute(ret);
//
//      Assert.assertTrue(dbManager.getDynamicPropertiesStore().getTotalEnergyWeight() >= 0);
//    } catch (ContractValidateException e) {
//      Assert.assertTrue(e instanceof ContractValidateException);
//    } catch (ContractExeException e) {
//      Assert.assertTrue(e instanceof ContractExeException);
//    }
//  }

}

