/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;

import java.io.File;

@Slf4j
public class TransferAssetActuatorTest {

	private static final String dbPath = "output_transfer_asset_test";
	private static final String ASSET_NAME = "mcash";
	private static final String OWNER_ADDRESS;
	private static final String TO_ADDRESS;
	private static final String NOT_EXIT_ADDRESS;
	private static final String NOT_EXIT_ADDRESS_2;
	private static final long OWNER_ASSET_BALANCE = 99999;
	private static final String ownerAsset_ADDRESS;
	private static final String ownerASSET_NAME = "mcashtest";
	private static final long OWNER_ASSET_Test_BALANCE = 99999;
	private static final String OWNER_ADDRESS_INVALID = "cccc";
	private static final String TO_ADDRESS_INVALID = "dddd";
	private static final long TOTAL_SUPPLY = 10L;
	private static final int TRX_NUM = 10;
	private static final int NUM = 1;
	private static final long START_TIME = 1;
	private static final long END_TIME = 2;
	private static final int VOTE_SCORE = 2;
	private static final String DESCRIPTION = "MCASH";
	private static final String URL = "https://mcash.network";
	private static ApplicationContext context;
	private static Manager dbManager;
	private static Any contract;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
		TO_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a146a";
		NOT_EXIT_ADDRESS = Wallet.getAddressPreFixString() + "B56446E617E924805E4D6CA021D341FEF6E2013B";
		NOT_EXIT_ADDRESS_2 = Wallet.getAddressPreFixString() + "B56446E617E924805E4D6CA021D341FEF6E21234";
		ownerAsset_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049010";
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
		AccountCapsule toAccountCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
				ByteString.copyFromUtf8("toAccount"),
				AccountType.Normal);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	private boolean isNullOrZero(Long value) {
		if (null == value || value == 0) {
			return true;
		}
		return false;
	}

	public void createAsset(String assetName) {
		AccountCapsule ownerCapsule = dbManager.getAccountStore()
			.get(ByteArray.fromHexString(OWNER_ADDRESS));

		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		ownerCapsule.addAsset(id, OWNER_ASSET_BALANCE);
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString(assetName)))
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
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAssetIssueStore()
			.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
	}

	private Any getContract(long sendCoin) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		return Any.pack(
			Contract.TransferAssetContract.newBuilder()
				.setAssetId(tokenIdNum)
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAmount(sendCoin)
				.build());
	}

	private Any getContract(long sendCoin, long assetId) {
		return Any.pack(
			Contract.TransferAssetContract.newBuilder()
				.setAssetId(assetId)
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
				.setAmount(sendCoin)
				.build());
	}

	private Any getContract(long sendCoin, String owner, String to) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		return Any.pack(
			Contract.TransferAssetContract.newBuilder()
				.setAssetId(tokenIdNum)
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owner)))
				.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(to)))
				.setAmount(sendCoin)
				.build());
	}

	private void createAsset() {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
			AssetIssueContract.newBuilder()
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

		AccountCapsule ownerCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
				ByteString.copyFromUtf8("owner"),
				AccountType.AssetIssue);

		ownerCapsule.addAsset(id, OWNER_ASSET_BALANCE);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
	}

	@Test
	public void successTransfer() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(100L), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(
				owner.getInstance().getAssetsMap().get(tokenIdNum).longValue(),
				OWNER_ASSET_BALANCE - 100);
			Assert.assertEquals(
				toAccount.getInstance().getAssetsMap().get(tokenIdNum).longValue(),
				100L);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void successTransfer2() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner =
				dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
				dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(
				owner.getInstance().getAssetsMap().get(tokenIdNum).longValue(), 0L);
			Assert.assertEquals(
				toAccount.getInstance().getAssetsMap().get(tokenIdNum).longValue(),
				OWNER_ASSET_BALANCE);
		} catch (ContractValidateException | ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void ownerNoAsset() {
		createAsset();
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setInstance(owner.getInstance().toBuilder().clearAssets().build());
		dbManager.getAccountStore().put(owner.createDbKey(), owner);
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Owner no asset!");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Owner no asset!", e.getMessage());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void notEnoughAssetTest() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE + 1),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertEquals("assetBalance is not sufficient.", e.getMessage());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void zeroAmount() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(0), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Amount must greater than 0.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Amount must greater than 0.", e.getMessage());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void negativeAmount() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(-999), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Amount must greater than 0.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Amount must greater than 0.", e.getMessage());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noneExistAsset() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(1, 10000000L),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("No asset !");
		} catch (ContractValidateException e) {
			Assert.assertEquals("No asset !", e.getMessage());
			Assert.assertEquals(ret.getInstance().getCode(), Code.SUCCESS);
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void noExistToAccount() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(
			getContract(100L, OWNER_ADDRESS, NOT_EXIT_ADDRESS_2), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			AccountCapsule noExitAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
			Assert.assertNull(noExitAccount);
			actuator.validate();
			actuator.execute(ret);
			noExitAccount = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
			Assert.assertNotNull(noExitAccount);    //Had created.
			Assert.assertEquals(noExitAccount.getBalance(), 0);
			actuator.execute(ret);
		} catch (ContractValidateException e) {
			Assert.assertEquals("Validate TransferAssetActuator error, insufficient fee.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			AccountCapsule noExitAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
			Assert.assertNull(noExitAccount);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void addOverflowTest() {
		createAsset();
		// First, increase the to balance. Else can't complete this test case.
		AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		toAccount.addAsset(tokenIdNum, Long.MAX_VALUE);
		dbManager.getAccountStore().put(ByteArray.fromHexString(TO_ADDRESS), toAccount);
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(1), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("long overflow");
		} catch (ContractValidateException e) {
			Assert.assertEquals("long overflow", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertEquals(toAccount.getAssetMap().get(tokenIdNum).longValue(), Long.MAX_VALUE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void transferToYourself() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(
			getContract(100L, OWNER_ADDRESS, OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Cannot transfer asset to yourself.");

		} catch (ContractValidateException e) {
			Assert.assertEquals("Cannot transfer asset to yourself.", e.getMessage());

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidOwnerAddress() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(
			getContract(100L, OWNER_ADDRESS_INVALID, TO_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid ownerAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid ownerAddress", e.getMessage());

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void invalidToAddress() {
		createAsset();
		TransferAssetActuator actuator = new TransferAssetActuator(
			getContract(100L, OWNER_ADDRESS, TO_ADDRESS_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("Invalid toAddress");
		} catch (ContractValidateException e) {
			Assert.assertEquals("Invalid toAddress", e.getMessage());

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertEquals(owner.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void ownerZeroAssetBalance() {
		createAsset();
		long tokenIdNum = 2000000;
		AccountCapsule ownerAssetCapsule =
			new AccountCapsule(
				ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)),
				ByteString.copyFromUtf8("ownerAsset"),
				AccountType.AssetIssue);
		ownerAssetCapsule.addAsset(tokenIdNum, OWNER_ASSET_Test_BALANCE);

		AssetIssueContract assetIssueTestContract =
			AssetIssueContract.newBuilder()
				.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)))
				.setName(ByteString.copyFrom(ByteArray.fromString(ownerASSET_NAME)))
				.setTotalSupply(TOTAL_SUPPLY)
				.setMcashNum(TRX_NUM)
				.setId(tokenIdNum)
				.setNum(NUM)
				.setStartTime(START_TIME)
				.setEndTime(END_TIME)
				.setVoteScore(VOTE_SCORE)
				.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
				.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
				.build();

		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueTestContract);
		dbManager.getAccountStore()
			.put(ownerAssetCapsule.getAddress().toByteArray(), ownerAssetCapsule);
		dbManager.getAssetIssueStore()
			.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		TransferAssetActuator actuator = new TransferAssetActuator(
			getContract(1, tokenIdNum),
			dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail("assetBalance must greater than 0.");
		} catch (ContractValidateException e) {
			Assert.assertEquals("assetBalance must greater than 0.", e.getMessage());

			AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long secondTokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();

			Assert.assertEquals(owner.getAssetMap().get(secondTokenIdNum).longValue(), OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(tokenIdNum)));

			AccountCapsule ownerAsset = dbManager.getAccountStore().get(ByteArray.fromHexString(ownerAsset_ADDRESS));

			Assert.assertEquals(ownerAsset.getAssetMap().get(tokenIdNum).longValue(), OWNER_ASSET_Test_BALANCE);
		} catch (ContractExeException e) {
			Assert.fail(e.getMessage());
		}
	}

}
