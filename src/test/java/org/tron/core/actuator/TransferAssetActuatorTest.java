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

package org.tron.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.AssetIssueCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class TransferAssetActuatorTest {

  private static TronApplicationContext context;
  private static Manager dbManager;
  private static Any contract;
  private static final String dbPath = "output_transferasset_test";
  private static final String ASSET_NAME = "mcash";
  private static final String OWNER_ADDRESS;
  private static final String TO_ADDRESS;
  private static final String NOT_EXIT_ADDRESS;
  private static final String NOT_EXIT_ADDRESS_2;
  private static final long OWNER_ASSET_BALANCE = 99999;
  private static final String ownerAsset_ADDRESS;
  private static final String ownerASSET_NAME = "trxtest";
  private static final long OWNER_ASSET_Test_BALANCE = 99999;
  private static final String OWNER_ADDRESS_INVALID = "cccc";
  private static final String TO_ADDRESS_INVALID = "dddd";
  private static final long TOTAL_SUPPLY = 10L;
  private static final int TRX_NUM = 10;
  private static final int NUM = 1;
  private static final long START_TIME = 1;
  private static final long END_TIME = 2;
  private static final int VOTE_SCORE = 2;
  private static final String DESCRIPTION = "TRX";
  private static final String URL = "https://tron.network";

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049150";
    TO_ADDRESS = "548794500882809695a8a687866e76d4271a146a";
    NOT_EXIT_ADDRESS = "B56446E617E924805E4D6CA021D341FEF6E2013B";
    NOT_EXIT_ADDRESS_2 = "B56446E617E924805E4D6CA021D341FEF6E21234";
    ownerAsset_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049010";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
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

  public void createAsset(String assetName) {
    AccountCapsule ownerCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    ownerCapsule.addAsset(assetName.getBytes(), OWNER_ASSET_BALANCE);

    long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
    dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
    AssetIssueContract assetIssueContract =
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromString(assetName)))
            .setId(Long.toString(id))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
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
    String assertName = ASSET_NAME;
    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      assertName = String.valueOf(tokenIdNum);
    }

    return Any.pack(
        Contract.TransferAssetContract.newBuilder()
            .setAssetName(ByteString.copyFrom(ByteArray.fromString(assertName)))
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
            .setAmount(sendCoin)
            .build());
  }

  private Any getContract(long sendCoin, ByteString assetName) {
    return Any.pack(
        Contract.TransferAssetContract.newBuilder()
            .setAssetName(assetName)
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
            .setAmount(sendCoin)
            .build());
  }

  private Any getContract(long sendCoin, String assetName) {
    return Any.pack(
        Contract.TransferAssetContract.newBuilder()
            .setAssetName(ByteString.copyFrom(ByteArray.fromString(assetName)))
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
            .setAmount(sendCoin)
            .build());
  }

  private Any getContract(long sendCoin, String owner, String to) {
    String assertName = ASSET_NAME;
    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      assertName = String.valueOf(tokenIdNum);
    }
    return Any.pack(
        Contract.TransferAssetContract.newBuilder()
            .setAssetName(ByteString.copyFrom(ByteArray.fromString(assertName)))
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owner)))
            .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(to)))
            .setAmount(sendCoin)
            .build());
  }

  private void createAssertBeforSameTokenNameActive() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
    dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
    AssetIssueContract assetIssueContract =
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
            .setId(Long.toString(id))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(START_TIME)
            .setEndTime(END_TIME)
            .setVoteScore(VOTE_SCORE)
            .setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
            .setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
            .build();
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
    dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
    dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);

    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            ByteString.copyFromUtf8("owner"),
            AccountType.AssetIssue);
    ownerCapsule.addAsset(ASSET_NAME.getBytes(), OWNER_ASSET_BALANCE);
    ownerCapsule.addAssetV2(ByteArray.fromString(String.valueOf(id)), OWNER_ASSET_BALANCE);

    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
  }

  private void createAssertSameTokenNameActive() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
    dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
    AssetIssueContract assetIssueContract =
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
            .setId(Long.toString(id))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(START_TIME)
            .setEndTime(END_TIME)
            .setVoteScore(VOTE_SCORE)
            .setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
            .setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
            .build();
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
    dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);

    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            ByteString.copyFromUtf8("owner"),
            AccountType.AssetIssue);

    ownerCapsule.addAssetV2(ByteArray.fromString(String.valueOf(id)), OWNER_ASSET_BALANCE);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
  }

  /**
   * SameTokenName close, transfer assert success.
   */
  @Test
  public void SameTokenNameCloseSuccessTransfer() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(100L), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      // check V1
      Assert.assertEquals(owner.getInstance().getAssetMap().get(ASSET_NAME).longValue(),
          OWNER_ASSET_BALANCE - 100);
      Assert.assertEquals(toAccount.getInstance().getAssetMap().get(ASSET_NAME).longValue(), 100L);
      // check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(
          owner.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE - 100);
      Assert.assertEquals(
          toAccount.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          100L);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open, transfer assert success.
   */
  @Test
  public void SameTokenNameOpenSuccessTransfer() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(100L), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      // V1, data is not exist
      Assert.assertNull(owner.getAssetMap().get(ASSET_NAME));
      Assert.assertNull(toAccount.getAssetMap().get(ASSET_NAME));
      // check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(
          owner.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE - 100);
      Assert.assertEquals(
          toAccount.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          100L);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close, transfer assert success.
   */
  @Test
  public void SameTokenNameCloseSuccessTransfer2() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      //check V1
      Assert.assertEquals(owner.getInstance().getAssetMap().get(ASSET_NAME).longValue(), 0L);
      Assert.assertEquals(
          toAccount.getInstance().getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      //check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(
          owner.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(), 0L);
      Assert.assertEquals(
          toAccount.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * Init close SameTokenName,after init data,open SameTokenName
   */
  @Test
  public void OldNotUpdateSuccessTransfer2() {
    createAssertBeforSameTokenNameActive();
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      //check V1
      Assert.assertEquals(owner.getInstance().getAssetMap().get(ASSET_NAME).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertNull(toAccount.getInstance().getAssetMap().get(ASSET_NAME));
      //check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(
          owner.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(), 0L);
      Assert.assertEquals(
          toAccount.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open, transfer assert success.
   */
  @Test
  public void SameTokenNameOpenSuccessTransfer2() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      // V1, data is not exist
      Assert.assertNull(owner.getAssetMap().get(ASSET_NAME));
      Assert.assertNull(toAccount.getAssetMap().get(ASSET_NAME));
      //check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(
          owner.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(), 0L);
      Assert.assertEquals(
          toAccount.getInstance().getAssetV2Map().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close,no Assert.
   */
  @Test
  public void SameTokenNameCloseOwnerNoAssetTest() {
    createAssertBeforSameTokenNameActive();
    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setInstance(owner.getInstance().toBuilder().clearAsset().build());
    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Owner no asset!", e.getMessage());
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open,no Assert.
   */
  @Test
  public void SameTokenNameOpenOwnerNoAssetTest() {
    createAssertSameTokenNameActive();
    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setInstance(owner.getInstance().toBuilder().clearAssetV2().build());
    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Owner no asset!", e.getMessage());
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close,Unit test.
   */
  @Test
  public void SameTokenNameCloseNotEnoughAssetTest() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE + 1),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("assetBalance is not sufficient.".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open,Unit test.
   */
  @Test
  public void SameTokenNameOpenNotEnoughAssetTest() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE + 1),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("assetBalance is not sufficient.".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close, zere amount
   */
  @Test
  public void SameTokenNameCloseZeroAmountTest() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(0), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Amount must greater than 0.".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * SameTokenName open, zere amount
   */
  @Test
  public void SameTokenNameOpenZeroAmountTest() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(0), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Amount must greater than 0.".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close, negative amount
   */
  @Test
  public void SameTokenNameCloseNegativeAmountTest() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(-999), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Amount must greater than 0.".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * SameTokenName open, negative amount
   */
  @Test
  public void SameTokenNameOpenNegativeAmountTest() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(-999), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Amount must greater than 0.".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close, no exist assert
   */
  @Test
  public void SameTokenNameCloseNoneExistAssetTest() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(1, "TTTTTTTTTTTT"),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("No asset !".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open, no exist assert
   */
  @Test
  public void SameTokenNameOpenNoneExistAssetTest() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(1, "TTTTTTTTTTTT"),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("No asset !".equals(e.getMessage()));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * SameTokenName close,If to account not exit, create it.
   */
  @Test
  public void SameTokenNameCloseNoExitToAccount() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS, NOT_EXIT_ADDRESS), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      AccountCapsule noExitAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(NOT_EXIT_ADDRESS));
      Assert.assertTrue(null == noExitAccount);
      actuator.validate();
      actuator.execute(ret);
      noExitAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(NOT_EXIT_ADDRESS));
      Assert.assertFalse(null == noExitAccount);    //Had created.
      Assert.assertEquals(noExitAccount.getBalance(), 0);
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert
          .assertEquals("Validate TransferAssetActuator error, insufficient fee.", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      AccountCapsule noExitAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(NOT_EXIT_ADDRESS));
      Assert.assertTrue(noExitAccount == null);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open,If to account not exit, create it.
   */
  @Test
  public void SameTokenNameOpenNoExitToAccount() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS, NOT_EXIT_ADDRESS_2), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      AccountCapsule noExitAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
      Assert.assertTrue(null == noExitAccount);
      actuator.validate();
      actuator.execute(ret);
      noExitAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
      Assert.assertFalse(null == noExitAccount);    //Had created.
      Assert.assertEquals(noExitAccount.getBalance(), 0);
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert
          .assertEquals("Validate TransferAssetActuator error, insufficient fee.", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      AccountCapsule noExitAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
      Assert.assertTrue(noExitAccount == null);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close, add over flow
   */
  @Test
  public void SameTokenNameCloseAddOverflowTest() {
    createAssertBeforSameTokenNameActive();
    // First, increase the to balance. Else can't complete this test case.
    AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
    toAccount.addAsset(ASSET_NAME.getBytes(), Long.MAX_VALUE);
    dbManager.getAccountStore().put(ByteArray.fromHexString(TO_ADDRESS), toAccount);
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(1), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("long overflow".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertEquals(toAccount.getAssetMap().get(ASSET_NAME).longValue(), Long.MAX_VALUE);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * SameTokenName open, add over flow
   */
  @Test
  public void SameTokenNameOpenAddOverflowTest() {
    createAssertSameTokenNameActive();
    // First, increase the to balance. Else can't complete this test case.
    AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
    long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    toAccount.addAssetV2(ByteArray.fromString(String.valueOf(tokenIdNum)), Long.MAX_VALUE);
    dbManager.getAccountStore().put(ByteArray.fromHexString(TO_ADDRESS), toAccount);
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(1), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("long overflow".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertEquals(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          Long.MAX_VALUE);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close,transfer asset to yourself,result is error
   */
  @Test
  public void SameTokenNameCloseTransferToYourself() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS, OWNER_ADDRESS), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Cannot transfer asset to yourself.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Cannot transfer asset to yourself.", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * SameTokenName open,transfer asset to yourself,result is error
   */
  @Test
  public void SameTokenNameOpenTransferToYourself() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS, OWNER_ADDRESS), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Cannot transfer asset to yourself.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Cannot transfer asset to yourself.", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close,Invalid ownerAddress,result is error
   */
  @Test
  public void SameTokenNameCloseInvalidOwnerAddress() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS_INVALID, TO_ADDRESS), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid ownerAddress");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid ownerAddress", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open,Invalid ownerAddress,result is error
   */
  @Test
  public void SameTokenNameOpenInvalidOwnerAddress() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS_INVALID, TO_ADDRESS), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid ownerAddress");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid ownerAddress", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close,Invalid ToAddress,result is error
   */
  @Test
  public void SameTokenNameCloseInvalidToAddress() {
    createAssertBeforSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS, TO_ADDRESS_INVALID), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid toAddress");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid toAddress", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open,Invalid ToAddress,result is error
   */
  @Test
  public void SameTokenNameOpenInvalidToAddress() {
    createAssertSameTokenNameActive();
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(100L, OWNER_ADDRESS, TO_ADDRESS_INVALID), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid toAddress");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid toAddress", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(TO_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close,Account do not have this asset,Transfer this Asset result is failed
   */
  @Test
  public void SameTokenNameCloseOwnerNoThisAsset() {
    createAssertBeforSameTokenNameActive();
    AccountCapsule ownerAssetCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)),
            ByteString.copyFromUtf8("ownerAsset"),
            AccountType.AssetIssue);
    ownerAssetCapsule.addAsset(ownerASSET_NAME.getBytes(), OWNER_ASSET_Test_BALANCE);
    AssetIssueContract assetIssueTestContract =
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromString(ownerASSET_NAME)))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
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
    dbManager
        .getAssetIssueStore()
        .put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(1, ownerASSET_NAME),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("assetBalance must greater than 0.".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getAssetMap().get(ASSET_NAME).longValue(), OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMap().get(ASSET_NAME)));
      AccountCapsule ownerAsset =
          dbManager.getAccountStore().get(ByteArray.fromHexString(ownerAsset_ADDRESS));
      Assert.assertEquals(ownerAsset.getAssetMap().get(ownerASSET_NAME).longValue(),
          OWNER_ASSET_Test_BALANCE);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName open,Account do not have this asset,Transfer this Asset result is failed
   */
  @Test
  public void SameTokenNameOpenOwnerNoThisAsset() {
    createAssertSameTokenNameActive();
    long tokenIdNum = 2000000;
    AccountCapsule ownerAssetCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)),
            ByteString.copyFromUtf8("ownerAsset"),
            AccountType.AssetIssue);
    ownerAssetCapsule.addAssetV2(ByteArray.fromString(String.valueOf(tokenIdNum)),
        OWNER_ASSET_Test_BALANCE);

    AssetIssueContract assetIssueTestContract =
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromString(ownerASSET_NAME)))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setId(String.valueOf(tokenIdNum))
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
    dbManager.getAssetIssueV2Store()
        .put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);
    TransferAssetActuator actuator = new TransferAssetActuator(
        getContract(1, String.valueOf(tokenIdNum)),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("assetBalance must greater than 0.".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      long secondTokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(secondTokenIdNum)).longValue(),
          OWNER_ASSET_BALANCE);
      Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(String.valueOf(tokenIdNum))));
      AccountCapsule ownerAsset =
          dbManager.getAccountStore().get(ByteArray.fromHexString(ownerAsset_ADDRESS));
      Assert.assertEquals(ownerAsset.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          OWNER_ASSET_Test_BALANCE);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName close, No owner account!
   */
  @Test
  public void sameTokenNameCloseNoOwnerAccount() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
    dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
    AssetIssueContract assetIssueContract =
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
            .setId(Long.toString(id))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(START_TIME)
            .setEndTime(END_TIME)
            .setVoteScore(VOTE_SCORE)
            .setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
            .setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
            .build();
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
    dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

    dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));

    TransferAssetActuator actuator = new TransferAssetActuator(getContract(100L), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("No owner account!".equals(e.getMessage()));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  /**
   * SameTokenName close,Asset name length must between 1 to 32 and can not contain space and other unreadable character, and can not contain chinese characters.
   */

  //asset name validation which is unnecessary has been removed!
  public void SameTokenNameCloseAssetNameTest() {
    createAssertBeforSameTokenNameActive();
    //Empty name, throw exception
    ByteString emptyName = ByteString.EMPTY;
    TransferAssetActuator actuator = new TransferAssetActuator(getContract(100L, emptyName),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("No asset !", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    //Too long name, throw exception. Max long is 32.
    String assetName = "testname0123456789abcdefghijgklmo";
//    actuator = new TransferAssetActuator(getContract(100L, assetName),
//        dbManager);
//    try {
//      actuator.validate();
//      actuator.execute(ret);
//      Assert.assertTrue(false);
//    } catch (ContractValidateException e) {
//      Assert.assertTrue(e instanceof ContractValidateException);
//      Assert.assertEquals("Invalid assetName", e.getMessage());
//      AccountCapsule toAccount =
//          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
//      Assert.assertTrue(
//          isNullOrZero(toAccount.getAssetMap().get(assetName)));
//    } catch (ContractExeException e) {
//      Assert.assertFalse(e instanceof ContractExeException);
//    }

    //Contain space, throw exception. Every character need readable .
//    assetName = "t e";
//    actuator = new TransferAssetActuator(getContract(100L, assetName), dbManager);
//    try {
//      actuator.validate();
//      actuator.execute(ret);
//      Assert.assertTrue(false);
//    } catch (ContractValidateException e) {
//      Assert.assertTrue(e instanceof ContractValidateException);
//      Assert.assertEquals("Invalid assetName", e.getMessage());
//      AccountCapsule toAccount =
//          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
//      Assert.assertTrue(
//          isNullOrZero(toAccount.getAssetMap().get(assetName)));
//    } catch (ContractExeException e) {
//      Assert.assertFalse(e instanceof ContractExeException);
//    }

    //Contain chinese character, throw exception.
//    actuator = new TransferAssetActuator(getContract(100L, ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95"))), dbManager);
//    try {
//      actuator.validate();
//      actuator.execute(ret);
//      Assert.assertTrue(false);
//    } catch (ContractValidateException e) {
//      Assert.assertTrue(e instanceof ContractValidateException);
//      Assert.assertEquals("Invalid assetName", e.getMessage());
//      AccountCapsule toAccount =
//          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
//      Assert.assertTrue(
//          isNullOrZero(toAccount.getAssetMap().get(assetName)));
//    } catch (ContractExeException e) {
//      Assert.assertFalse(e instanceof ContractExeException);
//    }

    // 32 byte readable character just ok.
    assetName = "testname0123456789abcdefghijgklm";
    createAsset(assetName);
    actuator = new TransferAssetActuator(getContract(100L, assetName), dbManager);
    try {
      actuator.validate();
      actuator.execute(ret);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getInstance().getAssetMap().get(assetName).longValue(),
          OWNER_ASSET_BALANCE - 100);
      Assert.assertEquals(toAccount.getInstance().getAssetMap().get(assetName).longValue(), 100L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    // 1 byte readable character ok.
    assetName = "t";
    createAsset(assetName);
    actuator = new TransferAssetActuator(getContract(100L, assetName), dbManager);
    try {
      actuator.validate();
      actuator.execute(ret);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AccountCapsule toAccount =
          dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
      Assert.assertEquals(owner.getInstance().getAssetMap().get(assetName).longValue(),
          OWNER_ASSET_BALANCE - 100);
      Assert.assertEquals(toAccount.getInstance().getAssetMap().get(assetName).longValue(), 100L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

}
