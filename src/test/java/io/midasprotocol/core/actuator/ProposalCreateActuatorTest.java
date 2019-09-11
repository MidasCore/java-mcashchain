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
import io.midasprotocol.core.capsule.ProposalCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;
import java.util.HashMap;

import static junit.framework.TestCase.fail;

@Slf4j
public class ProposalCreateActuatorTest {

	private static final String dbPath = "output_proposal_create_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String SUPERNODE_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String SUPERNODE_ADDRESS_SECOND;
	private static final String URL = "https://mcash.network";
	private static final String SUPERNODE_ADDRESS_INVALID = "aaaa";
	private static final String SUPERNODE_ADDRESS_NOACCOUNT;
	private static final String SUPERNODE_ADDRESS_BALANCENOTSUFFIENT;
	private static final String OWNER_ADDRESS;
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS_FIRST = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_SECOND = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_NOACCOUNT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
		SUPERNODE_ADDRESS_BALANCENOTSUFFIENT = Wallet.getAddressPreFixString()
			+ "548794500882809695a8a687866e06d4271a1ced";
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "84ca19269c61f4778e51a8ed085620d7ac1fc2ea";
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
	public void initTest() {
		WitnessCapsule ownerWitnessFirstCapsule =
			new WitnessCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				10_000_000L,
				URL);
		AccountCapsule ownerAccountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(""),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(300));
		ownerAccountCapsule.setIsCommittee(true);
		AccountCapsule ownerAccountFirstCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_FIRST),
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(300));
		AccountCapsule ownerAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(200_000));

		dbManager.getAccountStore()
			.put(ownerAccountCapsule.getAddress().toByteArray(), ownerAccountCapsule);
		dbManager.getAccountStore()
			.put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
		dbManager.getAccountStore()
			.put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);

		dbManager.getWitnessStore().put(ownerWitnessFirstCapsule.getAddress().toByteArray(),
			ownerWitnessFirstCapsule);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
		dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
	}

	private Any getContract(String address, HashMap<Long, Long> paras) {
		return Any.pack(
			Contract.ProposalCreateContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.putAllParameters(paras)
				.build());
	}

	/**
	 * first createProposal,result is success.
	 */
	@Test
	public void successProposalCreate() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(0L, 1000000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			long id = 1;
			ProposalCapsule proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			Assert.assertNotNull(proposalCapsule);
			Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 1);
			Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
			Assert.assertEquals(proposalCapsule.getCreateTime(), 1000000);
			Assert.assertEquals(proposalCapsule.getExpirationTime(),
				261200000); // 2000000 + 3 * 4 * 21600000
		} catch (ContractValidateException | ItemNotFoundException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Invalid Address, result is failed, exception is "Invalid address".
	 */
	@Test
	public void invalidAddress() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(0L, 10000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(SUPERNODE_ADDRESS_INVALID, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid address");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid address", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use AccountStore not exists, result is failed, exception is "account not exists".
	 */
	@Test
	public void noAccount() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(0L, 10000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(SUPERNODE_ADDRESS_NOACCOUNT, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + SUPERNODE_ADDRESS_NOACCOUNT + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + SUPERNODE_ADDRESS_NOACCOUNT + " does not exist",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use WitnessStore not exists Address,result is failed,exception is "witness not exists".
	 */
	@Test
	public void noWitness() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(0L, 10000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(SUPERNODE_ADDRESS_SECOND, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + SUPERNODE_ADDRESS_SECOND + " does not have right");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + SUPERNODE_ADDRESS_SECOND + " does not have right",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use invalid parameter, result is failed, exception is "Bad chain parameter id".
	 */
	@Test
	public void invalidPara() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(24L, 10000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Bad chain parameter id");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Bad chain parameter id",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		paras = new HashMap<>();
		paras.put(3L, 1 + 100_000_000_000_000_000L);
		actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Bad chain parameter value, valid range is [0, 100_000_000_000_000_000L]");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Bad chain parameter value, valid range is [0, 100_000_000_000_000_000L]",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * parameter size = 0 , result is failed, exception is "This proposal has no parameter.".
	 */
	@Test
	public void emptyProposal() {
		HashMap<Long, Long> paras = new HashMap<>();
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			fail("This proposal has no parameter");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This proposal has no parameter.",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void InvalidParaValue() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(8L, 1000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);

			fail("This value ALLOW_CREATION_OF_CONTRACTS is only allowed to be 1");
		} catch (ContractValidateException e) {
			Assert.assertEquals("This value ALLOW_CREATION_OF_CONTRACTS is only allowed to be 1",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/*
	 * two same proposal can work
	 */
	@Test
	public void duplicateProposalCreateSame() {
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(0L, 23 * 3600 * 1000L);
		paras.put(1L, 8_888_000_000L);
		paras.put(2L, 200_000L);
		paras.put(3L, 20L);
		paras.put(4L, 2048_000_000L);
		paras.put(5L, 64_000_000L);
		paras.put(6L, 64_000_000L);
		paras.put(7L, 64_000_000L);
		paras.put(8L, 1L);
		paras.put(9L, 64L);
		paras.put(10L, 64L);
		paras.put(11L, 64L);

		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		ProposalCreateActuator actuatorSecond =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);

		dbManager.getDynamicPropertiesStore().saveLatestProposalNum(0L);
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);

			actuatorSecond.validate();
			actuatorSecond.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);

			Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 2L);
			ProposalCapsule proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(2L));
			Assert.assertNotNull(proposalCapsule);

		} catch (ContractValidateException | ItemNotFoundException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}