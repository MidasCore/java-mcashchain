package org.tron.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.StringUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.witness.WitnessController;
import org.tron.protos.Contract;
import org.tron.protos.Contract.VoteWitnessContract;
import org.tron.protos.Contract.VoteWitnessContract.Vote;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class VoteWitnessActuatorTest {

  private static TronApplicationContext context;
  private static Manager dbManager;
  private static WitnessController witnessController;
  private static final String dbPath = "output_VoteWitness_test";
  private static final String ACCOUNT_NAME = "account";
  private static final String OWNER_ADDRESS;
  private static final String WITNESS_NAME = "witness";
  private static final String WITNESS_ADDRESS;
  private static final String URL = "https://tron.network";
  private static final String ADDRESS_INVALID = "aaaa";
  private static final String WITNESS_ADDRESS_NOACCOUNT;
  private static final String OWNER_ADDRESS_NOACCOUNT;
  private static final String OWNER_ADDRESS_BALANCENOTSUFFICIENT;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049abc";
    WITNESS_ADDRESS = "548794500882809695a8a687866e76d4271a1abc";
    WITNESS_ADDRESS_NOACCOUNT = "548794500882809695a8a687866e76d4271a1aed";
    OWNER_ADDRESS_NOACCOUNT = "548794500882809695a8a687866e76d4271a1aae";
    OWNER_ADDRESS_BALANCENOTSUFFICIENT = "548794500882809695a8a687866e06d4271a1ced";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    witnessController = dbManager.getWitnessController();
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    WitnessCapsule ownerCapsule =
        new WitnessCapsule(
            StringUtil.hexString2ByteString(WITNESS_ADDRESS),
            10L,
            URL);
    AccountCapsule witnessAccountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(WITNESS_NAME),
            StringUtil.hexString2ByteString(WITNESS_ADDRESS),
            AccountType.Normal,
            300L);
    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME),
            StringUtil.hexString2ByteString(OWNER_ADDRESS),
            AccountType.Normal,
            10_000_000_000_000L);

    dbManager.getAccountStore()
        .put(witnessAccountSecondCapsule.getAddress().toByteArray(), witnessAccountSecondCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    dbManager.getWitnessStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
  }

  private Any getContract(String address, String voteaddress, Long value) {
    return Any.pack(
        VoteWitnessContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(address))
            .setVote(Vote.newBuilder()
                .setVoteAddress(StringUtil.hexString2ByteString(voteaddress))
                .setVoteCount(value).build())
            .build());
  }

  private Any getRepeatedContract(String address, String voteaddress, Long value, int times) {
    VoteWitnessContract.Builder builder = VoteWitnessContract.newBuilder();
    builder.setOwnerAddress(StringUtil.hexString2ByteString(address));
    for (int i = 0; i < times; i++) {
      builder.setVote(Vote.newBuilder()
          .setVoteAddress(StringUtil.hexString2ByteString(voteaddress))
          .setVoteCount(value).build());
    }
    return Any.pack(builder.build());
  }

  private Any getContract(String ownerAddress, long frozenBalance, long duration) {
    return Any.pack(
        Contract.FreezeBalanceContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
            .setFrozenBalance(frozenBalance)
            .setFrozenDuration(duration)
            .build());
  }

  /**
   * voteWitness,result is success.
   */
  @Test
  public void voteWitness() {
    long frozenBalance = 1_000_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator freezeBalanceActuator = new FreezeBalanceActuator(
        getContract(OWNER_ADDRESS, frozenBalance, duration), dbManager);
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      freezeBalanceActuator.validate();
      freezeBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(1,
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteCount());
      Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteAddress().toByteArray());
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10 + 1, witnessCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Invalid ownerAddress voteWitness,result is failed,exception is "Invalid address".
   */
  @Test
  public void InvalidAddress() {
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(ADDRESS_INVALID, WITNESS_ADDRESS, 1L),
            dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid address");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use AccountStore not exists witness Address VoteWitness,result is failed,exception is "account
   * not exists".
   */
  @Test
  public void noAccount() {
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOACCOUNT, 1L),
            dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Account[" + WITNESS_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote());
      Assert.assertEquals("Account[" + WITNESS_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use WitnessStore not exists Address VoteWitness,result is failed,exception is "Witness not
   * exists".
   */
  @Test
  public void noWitness() {
    AccountCapsule accountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(WITNESS_NAME),
            StringUtil.hexString2ByteString(WITNESS_ADDRESS_NOACCOUNT),
            AccountType.Normal,
            300L);
    dbManager.getAccountStore()
        .put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOACCOUNT, 1L),
            dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Witness[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote());
      Assert.assertEquals("Witness[" + WITNESS_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * invalidVoteAddress
   */
  @Test
  public void invalidVoteAddress() {
    AccountCapsule accountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(WITNESS_NAME),
            StringUtil.hexString2ByteString(WITNESS_ADDRESS_NOACCOUNT),
            AccountType.Normal,
            300L);
    dbManager.getAccountStore()
        .put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, ADDRESS_INVALID, 1L),
            dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote());
      Assert.assertEquals("Invalid vote address!", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Every vote count must greater than 0.
   */
  @Test
  public void voteCountTest() {
    long frozenBalance = 1_000_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator freezeBalanceActuator = new FreezeBalanceActuator(
        getContract(OWNER_ADDRESS, frozenBalance, duration), dbManager);
    //0 votes
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 0L), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      freezeBalanceActuator.validate();
      freezeBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("vote count must be greater than 0", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    //-1 votes
    actuator = new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, -1L), dbManager);
    ret = new TransactionResultCapsule();
    try {
      freezeBalanceActuator.validate();
      freezeBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("vote count must be greater than 0", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * User can vote to 1 - 30 witnesses.
   */
  @Test
  public void voteCountsTest() {
    long frozenBalance = 1_000_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator freezeBalanceActuator = new FreezeBalanceActuator(
        getContract(OWNER_ADDRESS, frozenBalance, duration), dbManager);
    VoteWitnessActuator actuator = new VoteWitnessActuator(
        getRepeatedContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L, 0),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      freezeBalanceActuator.validate();
      freezeBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("VoteNumber must more than 0", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    actuator = new VoteWitnessActuator(getRepeatedContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L, 31),
        dbManager);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("VoteNumber more than maxVoteNumber 30", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Vote 1 witness one more times.
   */
  @Test
  public void vote1WitnssOneMoreTiems() {
    long frozenBalance = 1_000_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator freezeBalanceActuator = new FreezeBalanceActuator(
        getContract(OWNER_ADDRESS, frozenBalance, duration), dbManager);
    VoteWitnessActuator actuator = new VoteWitnessActuator(
        getRepeatedContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L, 30),
        dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      freezeBalanceActuator.validate();
      freezeBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);

      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10 + 30, witnessCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use AccountStore not exists ownerAddress VoteWitness,result is failed,exception is "account not
   * exists".
   */
  @Test
  public void noOwnerAccount() {
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS_NOACCOUNT, WITNESS_ADDRESS, 1L),
            dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * witnessAccount not freeze Balance, result is failed ,exception is "The total number of votes
   * 1000000 is greater than 0.
   */
  @Test
  public void balanceNotSufficient() {
    AccountCapsule balanceNotSufficientCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("balanceNotSufficient"),
            StringUtil.hexString2ByteString(OWNER_ADDRESS_BALANCENOTSUFFICIENT),
            AccountType.Normal,
            500L);
    dbManager.getAccountStore()
        .put(balanceNotSufficientCapsule.getAddress().toByteArray(), balanceNotSufficientCapsule);
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(
            getContract(OWNER_ADDRESS_BALANCENOTSUFFICIENT, WITNESS_ADDRESS, 1L),
            dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("The total number of votes[" + 1000000 + "] is greater than the tronPower["
          + balanceNotSufficientCapsule.getVotingPower() + "]");
    } catch (ContractValidateException e) {
      Assert.assertNull(dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS_BALANCENOTSUFFICIENT)).getVote());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert
          .assertEquals("The total number of votes[" + 1000000 + "] is greater than the tronPower["
              + balanceNotSufficientCapsule.getVotingPower() + "]", e.getMessage());
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Twice voteWitness,result is the last voteWitness.
   */
  @Test
  public void voteWitnessTwice() {
    long frozenBalance = 7_000_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator freezeBalanceActuator = new FreezeBalanceActuator(
        getContract(OWNER_ADDRESS, frozenBalance, duration), dbManager);
    VoteWitnessActuator actuator =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L), dbManager);
    VoteWitnessActuator actuatorTwice =
        new VoteWitnessActuator(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 3L), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      freezeBalanceActuator.validate();
      freezeBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      actuatorTwice.validate();
      actuatorTwice.execute(ret);
      Assert.assertEquals(3,
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteCount());
      Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVote().getVoteAddress().toByteArray());

      Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
      witnessController.updateWitness();
      WitnessCapsule witnessCapsule = witnessController
          .getWitnesseByAddress(StringUtil.hexString2ByteString(WITNESS_ADDRESS));
      Assert.assertEquals(13, witnessCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
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
}