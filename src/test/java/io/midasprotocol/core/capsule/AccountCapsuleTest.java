package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Key;
import io.midasprotocol.protos.Protocol.Permission;
import io.midasprotocol.protos.Protocol.Vote;
import org.junit.*;

import java.io.File;
import java.util.Map;
import java.util.Random;

@Ignore
public class AccountCapsuleTest {

	private static final String dbPath = "output_account_capsule_test";
	private static final Manager dbManager;
	private static final ApplicationContext context;
	private static final String OWNER_ADDRESS;
	private static final String ASSET_NAME = "mcash";
	private static final long TOTAL_SUPPLY = 10000L;
	private static final int TRX_NUM = 10;
	private static final int NUM = 1;
	private static final long START_TIME = 1;
	private static final long END_TIME = 2;
	private static final int VOTE_SCORE = 2;
	private static final String DESCRIPTION = "MCASH";
	private static final String URL = "https://midasprotocol.io";


	private static AccountCapsule accountCapsuleTest;
	private static AccountCapsule accountCapsule;

	static {
		Args.setParam(new String[]{"-d", dbPath, "-w"}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		dbManager = context.getBean(Manager.class);

		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "a06a17a49648a8ad32055c06f60fa14ae46df91234";
	}


	@BeforeClass
	public static void init() {
		ByteString accountName = ByteString.copyFrom(AccountCapsuleTest.randomBytes(16));
		ByteString address = ByteString.copyFrom(AccountCapsuleTest.randomBytes(32));
		AccountType accountType = AccountType.forNumber(1);
		accountCapsuleTest = new AccountCapsule(accountName, address, accountType);
		byte[] accountByte = accountCapsuleTest.getData();
		accountCapsule = new AccountCapsule(accountByte);
		accountCapsuleTest.setBalance(1111L);
	}

	@AfterClass
	public static void removeDb() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	private static byte[] randomBytes(int length) {
		//generate the random number
		byte[] result = new byte[length];
		new Random().nextBytes(result);
		return result;
	}

	@Test
	public void getDataTest() {
		//test AccountCapsule onstructed function
		Assert.assertEquals(accountCapsule.getInstance().getAccountName(),
			accountCapsuleTest.getInstance().getAccountName());
		Assert.assertEquals(accountCapsule.getInstance().getType(),
			accountCapsuleTest.getInstance().getType());
		Assert.assertEquals(1111, accountCapsuleTest.getBalance());
	}

	@Test
	public void addVotesTest() {
		//test addVote and getVote function
		ByteString voteAddress = ByteString.copyFrom(AccountCapsuleTest.randomBytes(32));
		long voteAdd = 10L;
		accountCapsuleTest.setVote(voteAddress, voteAdd);
		Vote vote = accountCapsuleTest.getVote();
		Assert.assertEquals(voteAddress, vote.getVoteAddress());
		Assert.assertEquals(voteAdd, vote.getVoteCount());
	}

	@Test
	public void AssetAmountTest() {
		//test AssetAmount ,addAsset and reduceAssetAmount function

		long assetId = 1L;
		long amountAdd = 222L;
		boolean addBoolean = accountCapsuleTest.addAssetAmount(assetId, amountAdd);

		Assert.assertTrue(addBoolean);

		Map<Long, Long> assetMap = accountCapsuleTest.getAssetMap();
		for (Map.Entry<Long, Long> entry : assetMap.entrySet()) {
			Assert.assertEquals(assetId, entry.getKey().longValue());
			Assert.assertEquals(amountAdd, entry.getValue().longValue());
		}
		long amountReduce = 22L;

		boolean reduceBoolean = accountCapsuleTest.reduceAssetAmount(assetId, amountReduce);
		Assert.assertTrue(reduceBoolean);

		Map<Long, Long> assetMapAfter = accountCapsuleTest.getAssetMap();
		for (Map.Entry<Long, Long> entry : assetMapAfter.entrySet()) {
			Assert.assertEquals(assetId, entry.getKey().longValue());
			Assert.assertEquals(amountAdd - amountReduce, entry.getValue().longValue());
		}
		long value = 11L;
		boolean addAsssetBoolean = accountCapsuleTest.addAsset(assetId, value);
		Assert.assertFalse(addAsssetBoolean);

		long assetId2 = 2L;
		long amountValue = 33L;
		boolean addAssetTrue = accountCapsuleTest.addAsset(assetId2, amountValue);
		Assert.assertTrue(addAssetTrue);
	}

	/**
	 * SameTokenName open, test assert amountV2 function
	 */
	@Test
	public void sameTokenNameOpenAssertAmountV2test() {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);

		Contract.AssetIssueContract assetIssueContract =
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
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
				.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		Contract.AssetIssueContract assetIssueContract2 =
			Contract.AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString("abc")))
				.setId(id + 1)
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setNum(NUM)
				.setStartTime(START_TIME)
				.setEndTime(END_TIME)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();
		AssetIssueCapsule assetIssueCapsule2 = new AssetIssueCapsule(assetIssueContract2);
		dbManager.getAssetIssueStore().put(assetIssueCapsule2.createDbKey(), assetIssueCapsule2);

		AccountCapsule accountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				10000);
		accountCapsule.addAsset(id, 1000L);
		dbManager.getAccountStore().put(accountCapsule.getAddress().toByteArray(), accountCapsule);
		Assert.assertEquals(accountCapsule.getAssetMap().get(id).longValue(), 1000L);

		//assetBalanceEnough
		Assert.assertTrue(accountCapsule.assetBalanceEnough(id, 999));

		Assert.assertFalse(accountCapsule.assetBalanceEnough(id, 1001));

		//reduceAssetAmount
		Assert.assertTrue(accountCapsule.reduceAssetAmount(id, 999));
		Assert.assertFalse(accountCapsule.reduceAssetAmount(id, 0));
		Assert.assertFalse(accountCapsule.reduceAssetAmount(id, 1001));
		// abc
		Assert.assertFalse(
			accountCapsule.reduceAssetAmount(id + 1, 1001));

		//addAssetAmount
		Assert.assertTrue(accountCapsule.addAssetAmount(id, 500));
		// 1000-999 +500
		Assert.assertEquals(accountCapsule.getAssetMap().get(id).longValue(), 501L);
		//abc
		Assert.assertTrue(accountCapsule.addAssetAmount(id + 1, 500));
		Assert.assertEquals(accountCapsule.getAssetMap().get(id + 1).longValue(), 500L);
	}

	@Test
	public void witnessPermissionTest() {
		AccountCapsule accountCapsule =
			new AccountCapsule(
				ByteString.copyFromUtf8("owner"),
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				AccountType.Normal,
				10000);

		Assert.assertArrayEquals(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule.getWitnessPermissionAddress());

		String witnessPermissionAddress = Wallet.getAddressPreFixString() +
			"cc6a17a49648a8ad32055c06f60fa14ae46df912cc";
		accountCapsule = new AccountCapsule(accountCapsule.getInstance().toBuilder().
			setWitnessPermission(Permission.newBuilder().addKeys(
				Key.newBuilder()
					.setAddress(ByteString.copyFrom(ByteArray.fromHexString(witnessPermissionAddress)))
					.build()).
				build()).build());

		Assert.assertArrayEquals(ByteArray.fromHexString(witnessPermissionAddress),
			accountCapsule.getWitnessPermissionAddress());
	}
}