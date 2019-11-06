package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.util.ConversionUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.StringUtil;
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
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Proposal.State;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;
import java.util.HashMap;

@Slf4j
public class ProposalDeleteActuatorTest {

	private static final String dbPath = "output_proposal_approve_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String SUPERNODE_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String SUPERNODE_ADDRESS_SECOND;
	private static final String URL = "https://mcash.network";
	private static final String SUPERNODE_ADDRESS_INVALID = "aaaa";
	private static final String SUPERNODE_ADDRESS_NOACCOUNT;
	private static final String OWNER_ADDRESS;
	private static ApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS_FIRST = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_SECOND = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_NOACCOUNT = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
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
				300_000_000L);
		AccountCapsule ownerAccountSecondCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND)),
				AccountType.Normal,
				200_000_000_000L);

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
		dbManager.getDynamicPropertiesStore().saveLatestProposalNum(0);

		long id = 1;
		dbManager.getProposalStore().delete(ByteArray.fromLong(1));
		dbManager.getProposalStore().delete(ByteArray.fromLong(2));
		HashMap<Long, Long> paras = new HashMap<>();
		paras.put(0L, 3 * 27 * 1000L);
		ProposalCreateActuator actuator =
			new ProposalCreateActuator(getContract(OWNER_ADDRESS, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			ProposalCapsule proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			Assert.assertNotNull(proposalCapsule);
			Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 1);
			Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
			Assert.assertEquals(proposalCapsule.getCreateTime(), 1000000);
			Assert.assertEquals(proposalCapsule.getExpirationTime(),
				261200000); // 2000000 + 3 * 4 * 21600000
		} catch (ContractValidateException | ContractExeException | ItemNotFoundException e) {
			Assert.fail(e.getMessage());
		}
	}

	private Any getContract(String address, HashMap<Long, Long> paras) {
		return Any.pack(
			Contract.ProposalCreateContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.putAllParameters(paras)
				.build());
	}

	private Any getContract(String address, long id) {
		return Any.pack(
			Contract.ProposalDeleteContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
				.setProposalId(id)
				.build());
	}

	/**
	 * first deleteProposal, result is success.
	 */
	@Test
	public void successDeleteApprove() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(OWNER_ADDRESS, id), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getState(), State.PENDING);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			try {
				proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			} catch (ItemNotFoundException e) {
				Assert.fail(e.getMessage());
				return;
			}
			Assert.assertEquals(proposalCapsule.getState(), State.CANCELED);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

	}

	/**
	 * use Invalid Address, result is failed, exception is "Invalid address".
	 */
	@Test
	public void invalidAddress() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(SUPERNODE_ADDRESS_INVALID, id), dbManager);
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

	/**
	 * use Account not exists, result is failed, exception is "account not exists".
	 */
	@Test
	public void noAccount() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(SUPERNODE_ADDRESS_NOACCOUNT, id), dbManager);
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
	 * Proposal is not proposed by witness, result is failed,exception is "witness not exists".
	 */
	@Test
	public void notProposed() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(SUPERNODE_ADDRESS_SECOND, id), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Proposal " + id + " " + "is not proposed by "
				+ StringUtil.createReadableString(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND))));
		} catch (ContractValidateException e) {
			Assert.assertEquals("Proposal " + id + " " + "is not proposed by "
					+ StringUtil.createReadableString(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_SECOND))),
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use Proposal not exists, result is failed, exception is "Proposal not exists".
	 */
	@Test
	public void noProposal() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 2;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(OWNER_ADDRESS, id), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Proposal " + id + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Proposal " + id + " does not exist",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Proposal expired, result is failed, exception is "Proposal expired".
	 */
	@Test
	public void proposalExpired() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(261200100);
		long id = 1;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(OWNER_ADDRESS, id), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Proposal " + id + " expired");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Proposal " + id + " expired",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Proposal canceled, result is failed, exception is "Proposal expired".
	 */
	@Test
	public void proposalCanceled() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(100100);
		long id = 1;

		ProposalDeleteActuator actuator = new ProposalDeleteActuator(
			getContract(OWNER_ADDRESS, id), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			proposalCapsule.setState(State.CANCELED);
			dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Proposal " + id + " canceled");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Proposal " + id + " canceled",
				e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}