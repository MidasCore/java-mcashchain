package io.midasprotocol.core.db;

import com.google.protobuf.ByteString;
import org.junit.*;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract.AccountCreateContract;
import io.midasprotocol.protos.Contract.TransferContract;
import io.midasprotocol.protos.Contract.VoteWitnessContract;
import io.midasprotocol.protos.Contract.WitnessCreateContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;

import java.io.File;
import java.util.Random;

@Ignore
public class TransactionStoreTest {

	private static final byte[] key1 = TransactionStoreTest.randomBytes(21);
	private static final byte[] key2 = TransactionStoreTest.randomBytes(21);
	private static final String URL = "https://midasprotocol.io";
	private static final String ACCOUNT_NAME = "ownerF";
	private static final String OWNER_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049abc";
	private static final String TO_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049abc";
	private static final long AMOUNT = 100;
	private static final String WITNESS_ADDRESS = "548794500882809695a8a687866e76d4271a1abc";
	private static String dbPath = "output_transaction_store_test";
	private static String dbDirectory = "db_transaction_store_test";
	private static String indexDirectory = "index_transaction_store_test";
	private static TransactionStore transactionStore;
	private static ApplicationContext context;
	private static Application AppT;
	private static Manager dbManager;

	static {
		Args.setParam(
			new String[]{
				"--output-directory", dbPath,
				"--storage-db-directory", dbDirectory,
				"--storage-index-directory", indexDirectory,
				"-w"
			},
			Constant.TEST_CONF
		);
		context = new ApplicationContext(DefaultConfig.class);
		AppT = ApplicationFactory.create(context);
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		transactionStore = dbManager.getTransactionStore();

	}

	@AfterClass
	public static void destroy() {
		Args.clearParam();
		AppT.shutdownServices();
		AppT.shutdown();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	static byte[] randomBytes(int length) {
		// generate the random number
		byte[] result = new byte[length];
		new Random().nextBytes(result);
		return result;
	}

	/**
	 * get AccountCreateContract.
	 */
	private AccountCreateContract getContract(String name, String address) {
		return AccountCreateContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
			.build();
	}

	/**
	 * get TransferContract.
	 */
	private TransferContract getContract(long count, String owneraddress, String toaddress) {
		return TransferContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owneraddress)))
			.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(toaddress)))
			.setAmount(count)
			.build();
	}

	/**
	 * get WitnessCreateContract.
	 */
	private WitnessCreateContract getWitnessContract(String address, String url) {
		return WitnessCreateContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
			.setUrl(ByteString.copyFrom(ByteArray.fromString(url)))
			.build();
	}

	/**
	 * get VoteWitnessContract.
	 */
	private VoteWitnessContract getVoteWitnessContract(String address, String voteaddress, Long value) {
		return VoteWitnessContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
			.setVoteAddress(ByteString.copyFrom(ByteArray.fromHexString(voteaddress)))
			.build();
	}

	@Test
	public void GetTransactionTest() throws BadItemException, ItemNotFoundException {
		final BlockStore blockStore = dbManager.getBlockStore();
		final TransactionStore transactionStore = dbManager.getTransactionStore();
		String key = "f31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62";

		BlockCapsule blockCapsule = new BlockCapsule(
			1,
			Sha256Hash.wrap(dbManager.getGenesisBlockId().getByteString()),
			1,
			ByteString.copyFrom(
				ECKey.fromPrivate(
					ByteArray.fromHexString(key)).getAddress()));

		// save in database with block number
		TransferContract tc = TransferContract.newBuilder()
			.setAmount(10)
			.setOwnerAddress(ByteString.copyFromUtf8("aaa"))
			.setToAddress(ByteString.copyFromUtf8("bbb"))
			.build();
		TransactionCapsule transactionCapsule = new TransactionCapsule(tc, ContractType.TransferContract);
		blockCapsule.addTransaction(transactionCapsule);
		transactionCapsule.setBlockNum(blockCapsule.getNum());
		blockStore.put(blockCapsule.getBlockId().getBytes(), blockCapsule);
		transactionStore.put(transactionCapsule.getTransactionId().getBytes(), transactionCapsule);
		Assert.assertEquals("Get transaction is error",
			transactionStore.get(transactionCapsule.getTransactionId().getBytes()).getInstance(),
			transactionCapsule.getInstance());

		// no found in transaction store database
		tc = TransferContract.newBuilder()
			.setAmount(1000)
			.setOwnerAddress(ByteString.copyFromUtf8("aaa"))
			.setToAddress(ByteString.copyFromUtf8("bbb"))
			.build();
		transactionCapsule = new TransactionCapsule(tc, ContractType.TransferContract);
		Assert.assertNull(transactionStore.get(transactionCapsule.getTransactionId().getBytes()));

		// no block number, directly save in database
		tc = TransferContract.newBuilder()
			.setAmount(10000)
			.setOwnerAddress(ByteString.copyFromUtf8("aaa"))
			.setToAddress(ByteString.copyFromUtf8("bbb"))
			.build();
		transactionCapsule = new TransactionCapsule(tc, ContractType.TransferContract);
		transactionStore.put(transactionCapsule.getTransactionId().getBytes(), transactionCapsule);
		Assert.assertEquals("Get transaction is error",
			transactionStore.get(transactionCapsule.getTransactionId().getBytes()).getInstance(),
			transactionCapsule.getInstance());
	}

	/**
	 * put and get CreateAccountTransaction.
	 */
	@Test
	public void CreateAccountTransactionStoreTest() throws BadItemException {
		AccountCreateContract accountCreateContract = getContract(ACCOUNT_NAME,
			OWNER_ADDRESS);
		TransactionCapsule ret = new TransactionCapsule(accountCreateContract,
			dbManager.getAccountStore());
		transactionStore.put(key1, ret);
		Assert.assertEquals("Store CreateAccountTransaction is error",
			transactionStore.get(key1).getInstance(),
			ret.getInstance());
		Assert.assertTrue(transactionStore.has(key1));
	}

	@Test
	public void GetUncheckedTransactionTest() {
		final BlockStore blockStore = dbManager.getBlockStore();
		final TransactionStore transactionStore = dbManager.getTransactionStore();
		String key = "f31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62";

		BlockCapsule blockCapsule = new BlockCapsule(
			1,
			Sha256Hash.wrap(dbManager.getGenesisBlockId().getByteString()),
			1,
			ByteString.copyFrom(
				ECKey.fromPrivate(
					ByteArray.fromHexString(key)).getAddress()));

		// save in database with block number
		TransferContract tc = TransferContract.newBuilder()
			.setAmount(10)
			.setOwnerAddress(ByteString.copyFromUtf8("aaa"))
			.setToAddress(ByteString.copyFromUtf8("bbb"))
			.build();
		TransactionCapsule transactionCapsule = new TransactionCapsule(tc, ContractType.TransferContract);
		blockCapsule.addTransaction(transactionCapsule);
		transactionCapsule.setBlockNum(blockCapsule.getNum());
		blockStore.put(blockCapsule.getBlockId().getBytes(), blockCapsule);
		transactionStore.put(transactionCapsule.getTransactionId().getBytes(), transactionCapsule);
		Assert.assertEquals("Get transaction is error",
			transactionStore.getUnchecked(transactionCapsule.getTransactionId().getBytes()).getInstance(),
			transactionCapsule.getInstance());

		// no found in transaction store database
		tc = TransferContract.newBuilder()
			.setAmount(1000)
			.setOwnerAddress(ByteString.copyFromUtf8("aaa"))
			.setToAddress(ByteString.copyFromUtf8("bbb"))
			.build();
		transactionCapsule = new TransactionCapsule(tc, ContractType.TransferContract);
		Assert.assertNull(transactionStore.getUnchecked(transactionCapsule.getTransactionId().getBytes()));

		// no block number, directly save in database
		tc = TransferContract.newBuilder()
			.setAmount(10000)
			.setOwnerAddress(ByteString.copyFromUtf8("aaa"))
			.setToAddress(ByteString.copyFromUtf8("bbb"))
			.build();
		transactionCapsule = new TransactionCapsule(tc, ContractType.TransferContract);
		transactionStore.put(transactionCapsule.getTransactionId().getBytes(), transactionCapsule);
		Assert.assertEquals("Get transaction is error",
			transactionStore.getUnchecked(transactionCapsule.getTransactionId().getBytes()).getInstance(),
			transactionCapsule.getInstance());
	}

	/**
	 * put and get CreateWitnessTransaction.
	 */
	@Test
	public void CreateWitnessTransactionStoreTest() throws BadItemException {
		WitnessCreateContract witnessContract = getWitnessContract(OWNER_ADDRESS, URL);
		TransactionCapsule transactionCapsule = new TransactionCapsule(witnessContract);
		transactionStore.put(key1, transactionCapsule);
		Assert.assertEquals("Store CreateWitnessTransaction is error",
			transactionStore.get(key1).getInstance(),
			transactionCapsule.getInstance());
	}

	/**
	 * put and get TransferTransaction.
	 */
	@Test
	public void TransferTransactionStorenTest() throws BadItemException {
		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.AssetIssue,
				1000000L
			);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		TransferContract transferContract = getContract(AMOUNT, OWNER_ADDRESS, TO_ADDRESS);
		TransactionCapsule transactionCapsule = new TransactionCapsule(transferContract,
			dbManager.getAccountStore());
		transactionStore.put(key1, transactionCapsule);
		Assert.assertEquals("Store TransferTransaction is error",
			transactionStore.get(key1).getInstance(),
			transactionCapsule.getInstance());
	}

	/**
	 * put and get VoteWitnessTransaction.
	 */

	@Test
	public void voteWitnessTransactionTest() throws BadItemException {

		AccountCapsule ownerAccountFirstCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8(ACCOUNT_NAME),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				ConversionUtil.McashToMatoshi(1_000_000));
		long frozenBalance = ConversionUtil.McashToMatoshi(1_000_000);
		long duration = 3;
		ownerAccountFirstCapsule.setFrozenForBandwidth(frozenBalance, duration);
		dbManager.getAccountStore()
			.put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
		VoteWitnessContract actuator = getVoteWitnessContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L);
		TransactionCapsule transactionCapsule = new TransactionCapsule(actuator);
		transactionStore.put(key1, transactionCapsule);
		Assert.assertEquals("Store VoteWitnessTransaction is error",
			transactionStore.get(key1).getInstance(),
			transactionCapsule.getInstance());
	}

	/**
	 * put value is null and get it.
	 */
	@Test
	public void TransactionValueNullTest() throws BadItemException {
		TransactionCapsule transactionCapsule = null;
		transactionStore.put(key2, transactionCapsule);
		Assert.assertNull("put value is null", transactionStore.get(key2));

	}

	/**
	 * put key is null and get it.
	 */
	@Test
	public void TransactionKeyNullTest() throws BadItemException {
		AccountCreateContract accountCreateContract = getContract(ACCOUNT_NAME,
			OWNER_ADDRESS);
		TransactionCapsule ret = new TransactionCapsule(accountCreateContract,
			dbManager.getAccountStore());
		byte[] key = null;
		transactionStore.put(key, ret);
		try {
			transactionStore.get(key);
		} catch (RuntimeException e) {
			Assert.assertEquals("The key argument cannot be null", e.getMessage());
		}
	}
}
