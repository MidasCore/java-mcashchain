package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.StringUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ProposalCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.util.ConversionUtil;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Proposal.State;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.io.File;
import java.util.HashMap;

@Slf4j

public class ProposalApproveActuatorTest {

	private static final String dbPath = "output_ProposalApprove_test";
	private static final String ACCOUNT_NAME_FIRST = "ownerF";
	private static final String SUPERNODE_ADDRESS_FIRST;
	private static final String ACCOUNT_NAME_SECOND = "ownerS";
	private static final String SUPERNODE_ADDRESS_SECOND;
	private static final String URL = "https://tron.network";
	private static final String SUPERNODE_ADDRESS_INVALID = "aaaa";
	private static final String SUPERNODE_ADDRESS_NOACCOUNT;
	private static final String OWNER_ADDRESS;
	private static TronApplicationContext context;
	private static Manager dbManager;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		SUPERNODE_ADDRESS_FIRST = "abd4b9367799eaa3197fecb144eb71de1e049abc";
		SUPERNODE_ADDRESS_SECOND = "548794500882809695a8a687866e76d4271a1abc";
		SUPERNODE_ADDRESS_NOACCOUNT = "548794500882809695a8a687866e76d4271a1aed";
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
		paras.put(0L, 6 * 27 * 1000L);
		ProposalCreateActuator actuator =
				new ProposalCreateActuator(getContract(SUPERNODE_ADDRESS_FIRST, paras), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
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

	private Any getContract(String address, long id, boolean isAddApproval) {
		return Any.pack(
				Contract.ProposalApproveContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
						.setProposalId(id)
						.setIsAddApproval(isAddApproval)
						.build());
	}

	/**
	 * first approveProposal, result is success.
	 */
	@Test
	public void successProposalApprove() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			try {
				proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			} catch (ItemNotFoundException e) {
				Assert.fail(e.getMessage());
				return;
			}
			Assert.assertEquals(proposalCapsule.getApprovals().size(), 1);
			Assert.assertEquals(proposalCapsule.getApprovals().get(0),
					ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)));
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}

		// isAddApproval == false
		ProposalApproveActuator actuator2 = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, 1, false), dbManager);
		TransactionResultCapsule ret2 = new TransactionResultCapsule();
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 1);
		try {
			actuator2.validate();
			actuator2.execute(ret2);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			try {
				proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			} catch (ItemNotFoundException e) {
				Assert.fail(e.getMessage());
				return;
			}
			Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
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

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_INVALID, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
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
	 * use AccountStore not exists, result is failed, exception is "account not exists".
	 */
	@Test
	public void noAccount() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_NOACCOUNT, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Account " + SUPERNODE_ADDRESS_NOACCOUNT + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Account " + SUPERNODE_ADDRESS_NOACCOUNT + " does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * use WitnessStore not exists Address,result is failed,exception is "witness not exists".
	 */
	@Test
	public void noWitness() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_SECOND, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + SUPERNODE_ADDRESS_SECOND + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness " + SUPERNODE_ADDRESS_SECOND + " does not exist",
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

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Proposal " + id + " does not exist");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Proposal " + id + " does not exist", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * duplicate approval, result is failed, exception is "Proposal not exists".
	 */
	@Test
	public void duplicateApproval() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
		long id = 1;

		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		proposalCapsule
				.addApproval(ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)));
		dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
		String readableOwnerAddress = StringUtil.createReadableString(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)));
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + readableOwnerAddress + " has approved "
					+ "proposal " + id + " before");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness " + readableOwnerAddress + " has approved "
					+ "proposal " + id + " before", e.getMessage());
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

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, id, true), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Proposal " + id + " expired");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Proposal " + id + " expired", e.getMessage());
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

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, id, true), dbManager);
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

	/**
	 * if !isAddApproval, and proposal not approved before, result is failed, exception is "Proposal
	 * expired".
	 */
	@Test
	public void proposalNotApproved() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(100100);
		long id = 1;

		// isAddApproval == true
		ProposalApproveActuator actuator = new ProposalApproveActuator(
				getContract(SUPERNODE_ADDRESS_FIRST, id, false), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		String readableOwnerAddress = StringUtil.createReadableString(
				ByteString.copyFrom(ByteArray.fromHexString(SUPERNODE_ADDRESS_FIRST)));
		ProposalCapsule proposalCapsule;
		try {
			proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
			dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
		} catch (ItemNotFoundException e) {
			Assert.fail(e.getMessage());
			return;
		}
		Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Witness " + readableOwnerAddress + " has not approved " + "proposal " + id + " before");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Witness " + readableOwnerAddress + " has not approved "
					+ "proposal " + id + " before", e.getMessage());
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}