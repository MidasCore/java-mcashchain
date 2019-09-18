package io.midasprotocol.core;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.BandwidthProcessor;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.db.TransactionTrace;
import io.midasprotocol.core.exception.AccountResourceInsufficientException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.TooBigTransactionResultException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Contract.TransferAssetContract;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;

@Slf4j
public class BandwidthProcessorTest {

	private static final String dbPath = "output_bandwidth_test";
	private static final String ASSET_NAME;
	private static final String OWNER_ADDRESS;
	private static final String ASSET_ADDRESS;
	private static final long ASSET_ID;
	private static final String TO_ADDRESS;
	private static final long TOTAL_SUPPLY = ConversionUtil.McashToMatoshi(10000000L);
	private static final int TRX_NUM = 2;
	private static final int NUM = 2147483647;
	private static final int VOTE_SCORE = 2;
	private static final String DESCRIPTION = "TRX";
	private static final String URL = "https://tron.network";
	private static Manager dbManager;
	private static ApplicationContext context;
	private static long START_TIME;
	private static long END_TIME;


	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		ASSET_NAME = "2";
		ASSET_ID = 2;
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		TO_ADDRESS = Wallet.getAddressPreFixString() + "9aad9b98a2e435707fa39539ea0b12cb4d80468d";
		ASSET_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a7890";
		START_TIME = DateTime.now().minusDays(1).getMillis();
		END_TIME = DateTime.now().getMillis();
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
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(getAssetIssueContract());
		assetIssueCapsule.setId(ASSET_ID);
		dbManager.getAssetIssueStore().put(ByteArray.fromLong(ASSET_ID), assetIssueCapsule);

		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				0L);
		ownerCapsule.addAsset(ASSET_ID, 100L);

		AccountCapsule toAccountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("toAccount"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
				AccountType.Normal,
				0L);

		AccountCapsule assetCapsule2 =
			new AccountCapsule(
				ByteString.copyFromUtf8("asset2"),
				ByteString.copyFrom(ByteArray.fromHexString(ASSET_ADDRESS)),
				AccountType.AssetIssue,
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());

//		dbManager.getAccountStore().reset();
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
		dbManager.getAccountStore().put(assetCapsule2.getAddress().toByteArray(), assetCapsule2);


		AccountCapsule temp = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
		logger.info(StringUtil.createReadableString(temp.getAddress()));
	}

	private TransferAssetContract getTransferAssetContract() {
		return Contract.TransferAssetContract.newBuilder()
			.setAssetId(ASSET_ID)
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
			.setAmount(100L)
			.build();
	}

	private AssetIssueContract getAssetIssueContract() {
		return Contract.AssetIssueContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ASSET_ADDRESS)))
			.setName(ByteString.copyFromUtf8(ASSET_NAME))
			.setId(ASSET_ID)
			.setFreeAssetBandwidthLimit(1000L)
			.setPublicFreeAssetBandwidthLimit(1000L)
			.build();
	}

	private void initAssetIssue(long startTimestmp, long endTimestmp, String assetName) {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString(assetName)))
				.setId(id)
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(startTimestmp)
				.setEndTime(endTimestmp)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		AccountCapsule toAccountCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(TO_ADDRESS));
		dbManager.getAssetIssueStore()
			.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		toAccountCapsule.addAsset(id, TOTAL_SUPPLY);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	@Test
	public void consumeAssetAccount() throws Exception {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
		dbManager.getDynamicPropertiesStore()
			.saveTotalBandwidthWeight(1_000_000_000L);//only assetAccount has frozen balance

		TransferAssetContract contract = getTransferAssetContract();
		TransactionCapsule trx = new TransactionCapsule(contract);

		// issuer freeze balance for bandwidth
		AccountCapsule issuerCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(ASSET_ADDRESS));
		issuerCapsule.setFrozenForBandwidth(ConversionUtil.McashToMatoshi(10), 0L);
		dbManager.getAccountStore().put(issuerCapsule.getAddress().toByteArray(), issuerCapsule);

		TransactionResultCapsule ret = new TransactionResultCapsule();
		TransactionTrace trace = new TransactionTrace(trx, dbManager);
		dbManager.consumeBandwidth(trx, trace);

		AccountCapsule ownerCapsuleNew = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		AccountCapsule issuerCapsuleNew = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(ASSET_ADDRESS));

		Assert.assertEquals(508882612, issuerCapsuleNew.getLatestBandwidthConsumeTime());
		Assert.assertEquals(1526647838000L, ownerCapsuleNew.getLatestOperationTime());
		Assert.assertEquals(508882612, ownerCapsuleNew.getLatestAssetOperationTime(ASSET_ID));
		Assert.assertEquals(112L + (dbManager.getDynamicPropertiesStore().supportVM()
				? Constant.MAX_RESULT_SIZE_IN_TX : 0),
			issuerCapsuleNew.getBandwidthUsage());
		Assert.assertEquals(112L + (dbManager.getDynamicPropertiesStore().supportVM()
				? Constant.MAX_RESULT_SIZE_IN_TX : 0),
			ownerCapsuleNew.getFreeAssetBandwidthUsage(ASSET_ID));

		Assert.assertEquals(508882612L, ownerCapsuleNew.getLatestAssetOperationTime(ASSET_ID));
		Assert.assertEquals(0L, ret.getFee());

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526691038000L); // + 12h

		dbManager.consumeBandwidth(trx, trace);

		ownerCapsuleNew = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));
		issuerCapsuleNew = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(ASSET_ADDRESS));

		Assert.assertEquals(508897012L, issuerCapsuleNew.getLatestBandwidthConsumeTime());
		Assert.assertEquals(1526691038000L, ownerCapsuleNew.getLatestOperationTime());
		Assert.assertEquals(508897012L,
			ownerCapsuleNew.getLatestAssetOperationTime(ASSET_ID));
		Assert.assertEquals(56L + 112L + (dbManager.getDynamicPropertiesStore().supportVM() ?
				Constant.MAX_RESULT_SIZE_IN_TX / 2 * 3 : 0),
			ownerCapsuleNew.getFreeAssetBandwidthUsage(ASSET_ID));
		Assert.assertEquals(56L + 112L + (dbManager.getDynamicPropertiesStore().supportVM() ?
				Constant.MAX_RESULT_SIZE_IN_TX / 2 * 3 : 0),
			issuerCapsuleNew.getBandwidthUsage());
		Assert.assertEquals(0L, ret.getFee());

	}

	/**
	 * sameTokenName open, consume success assetIssueCapsule.getOwnerAddress() !=
	 * fromAccount.getAddress()) contract.getType() = TransferAssetContract
	 */
	@Test
	public void consumeSuccess() {
		dbManager.getDynamicPropertiesStore().saveTotalBandwidthWeight(10_000_000L);

		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
				.setId(id)
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(START_TIME)
				.setEndTime(END_TIME)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.setPublicFreeAssetBandwidthLimit(2000)
				.setFreeAssetBandwidthLimit(2000)
				.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());
		ownerCapsule.setBalance(ConversionUtil.McashToMatoshi(10));
		long expireTime = DateTime.now().getMillis() + 6 * Parameter.TimeConstant.MS_PER_DAY;
		ownerCapsule.setFrozenForBandwidth(ConversionUtil.McashToMatoshi(2), expireTime);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		AccountCapsule toAddressCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
				AccountType.Normal,
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());
		toAddressCapsule.setBalance(ConversionUtil.McashToMatoshi(10));
		long expireTime2 = DateTime.now().getMillis() + 6 * Parameter.TimeConstant.MS_PER_DAY;
		toAddressCapsule.setFrozenForBandwidth(ConversionUtil.McashToMatoshi(2), expireTime2);
		dbManager.getAccountStore().put(toAddressCapsule.getAddress().toByteArray(), toAddressCapsule);

		TransferAssetContract contract = Contract.TransferAssetContract.newBuilder()
			.setAssetId(id)
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
			.setAmount(100L)
			.build();

		TransactionCapsule trx = new TransactionCapsule(contract);
		TransactionTrace trace = new TransactionTrace(trx, dbManager);

		long byteSize = trx.getInstance().toBuilder().clearRet().build().getSerializedSize() +
			Constant.MAX_RESULT_SIZE_IN_TX;

		BandwidthProcessor processor = new BandwidthProcessor(dbManager);

		try {
			processor.consume(trx, trace);
			Assert.assertEquals(trace.getReceipt().getBandwidthFee(), 0);
			Assert.assertEquals(trace.getReceipt().getBandwidthUsage(), byteSize);
			AccountCapsule ownerAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			Assert.assertNotNull(ownerAccount);
			Assert.assertEquals(ownerAccount.getBandwidthUsage(), byteSize);

			AssetIssueCapsule assetIssueCapsuleV2 =
				dbManager.getAssetIssueStore().get(assetIssueCapsule.createDbKey());
			Assert.assertNotNull(assetIssueCapsuleV2);
			Assert.assertEquals(assetIssueCapsuleV2.getPublicFreeAssetBandwidthUsage(), byteSize);

			AccountCapsule fromAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertNotNull(fromAccount);
			Assert.assertEquals(fromAccount.getFreeAssetBandwidthUsage(id), byteSize);
			Assert.assertEquals(fromAccount.getFreeAssetBandwidthUsage(id), byteSize);

		} catch (ContractValidateException | AccountResourceInsufficientException | TooBigTransactionResultException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
			dbManager.getAccountStore().delete(ByteArray.fromHexString(TO_ADDRESS));
			dbManager.getAssetIssueStore().delete(assetIssueCapsule.createDbKey());
		}
	}

	/**
	 * sameTokenName close, consume success contract.getType() = TransferContract toAddressAccount
	 * isn't exist.
	 */
	@Test
	public void transferToAccountNotExist() {
		dbManager.getDynamicPropertiesStore().saveTotalBandwidthWeight(10_000_000L);

		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());
		ownerCapsule.setBalance(ConversionUtil.McashToMatoshi(10));
		long expireTime = DateTime.now().getMillis() + 6 * Parameter.TimeConstant.MS_PER_DAY;
		ownerCapsule.setFrozenForBandwidth(ConversionUtil.McashToMatoshi(2), expireTime);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

		AccountCapsule toAddressCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
				AccountType.Normal,
				dbManager.getDynamicPropertiesStore().getAssetIssueFee());
		toAddressCapsule.setBalance(ConversionUtil.McashToMatoshi(10));
		long expireTime2 = DateTime.now().getMillis() + 6 * Parameter.TimeConstant.MS_PER_DAY;
		toAddressCapsule.setFrozenForBandwidth(ConversionUtil.McashToMatoshi(2), expireTime2);
		dbManager.getAccountStore().delete(toAddressCapsule.getAddress().toByteArray());

		Contract.TransferContract contract = Contract.TransferContract.newBuilder()
			.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
			.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
			.setAmount(100L)
			.build();

		TransactionCapsule trx = new TransactionCapsule(contract, dbManager.getAccountStore());
		TransactionTrace trace = new TransactionTrace(trx, dbManager);

		long byteSize = trx.getInstance().toBuilder().clearRet().build().getSerializedSize() +
			Constant.MAX_RESULT_SIZE_IN_TX;

		BandwidthProcessor processor = new BandwidthProcessor(dbManager);

		try {
			processor.consume(trx, trace);

			Assert.assertEquals(trace.getReceipt().getBandwidthFee(), 0);
			Assert.assertEquals(trace.getReceipt().getBandwidthUsage(), byteSize);
			AccountCapsule fromAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			Assert.assertNotNull(fromAccount);
			Assert.assertEquals(fromAccount.getBandwidthUsage(), byteSize);
		} catch (ContractValidateException | AccountResourceInsufficientException | TooBigTransactionResultException e) {
			Assert.fail(e.getMessage());
		} finally {
			dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
			dbManager.getAccountStore().delete(ByteArray.fromHexString(TO_ADDRESS));
		}
	}
}
