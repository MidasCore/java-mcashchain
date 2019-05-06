package stest.tron.wallet.onlinestress;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.AccountResourceMessage;
import org.tron.api.WalletGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.SmartContract;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractEvent001 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);

  private ManagedChannel channelFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  String txid;

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] event001Address = ecKey1.getAddress();
  String event001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] event002Address = ecKey2.getAddress();
  String event002Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true)
  public void test1ContractEventAndLog() {
    ecKey1 = new ECKey(Utils.getRandom());
    event001Address = ecKey1.getAddress();
    event001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    PublicMethed.printAddress(event001Key);

    ecKey2 = new ECKey(Utils.getRandom());
    event002Address = ecKey2.getAddress();
    event002Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
    PublicMethed.printAddress(event001Key);
    PublicMethed.printAddress(testKey002);

    Assert.assertTrue(PublicMethed.sendcoin(event001Address, maxFeeLimit * 30, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(event002Address, maxFeeLimit * 30, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull1);

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(event001Address,
        blockingStubFull);
    Long energyLimit = accountResource.getEnergyLimit();
    Long energyUsage = accountResource.getEnergyUsed();
    Long balanceBefore = PublicMethed.queryAccount(event001Key, blockingStubFull).getBalance();

    logger.info("before energy limit is " + Long.toString(energyLimit));
    logger.info("before energy usage is " + Long.toString(energyUsage));
    logger.info("before balance is " + Long.toString(balanceBefore));

    String contractName = "addressDemo";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_ContractEventAndLog1");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_ContractEventAndLog1");
    byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 50, null, event001Key, event001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getAbi() != null);

    Integer i = 0;
    for (i = 0; i < 1; i++) {
      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventCycle(uint256)", "100", false,
          1L, 100000000L, event002Address, event002Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForLogCycle(uint256)", "100", false,
          2L, 100000000L, event002Address, event002Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "triggerUintEvent()", "#", false,
          0, maxFeeLimit, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "triggerintEvent()", "#", false,
          0, maxFeeLimit, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventAndLog()", "#", false,
          1, maxFeeLimit, event001Address, event001Key, blockingStubFull);
      logger.info(txid);
      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventNoIndex()", "#", false,
          0L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);
      txid = PublicMethed.triggerContract(contractAddress,
          "depositForLog()", "#", false,
          1L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventNoIndex()", "#", false,
          1L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventOneIndex()", "#", false,
          1L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventTwoIndex()", "#", false,
          2L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEvent()", "#", false,
          3L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForEventCycle(uint256)", "100", false,
          1L, 100000000L, event002Address, event002Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForLogCycle(uint256)", "100", false,
          2L, 100000000L, event002Address, event002Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForAnonymousHasLog()", "#", false,
          4L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      txid = PublicMethed.triggerContract(contractAddress,
          "depositForAnonymousNoLog()", "#", false,
          5L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      String param = "\"" + code + "\"" + "," + "\"" + code + "\"";
      txid = PublicMethed.triggerContract(contractAddress,
          "triggerStringEvent(string,string)", param, false,
          0L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);

      param = "\"" + "true1" + "\"" + "," + "\"" + "false1" + "\"";
      txid = PublicMethed.triggerContract(contractAddress,
          "triggerBoolEvent(bool,bool)", param, false,
          0L, 100000000L, event001Address, event001Key, blockingStubFull);
      logger.info(txid);
      String filename = "/Users/wangzihe/Documents/modify_fullnode/java-tron/tooLongString.txt";
      try {
        FileReader fr = new FileReader(
            filename);
        InputStreamReader read = new InputStreamReader(new FileInputStream(new File(filename)));
        BufferedReader reader = new BufferedReader(read);
        String tooLongString = reader.readLine();
        param = "\"" + tooLongString + "\"" + "," + "\"" + tooLongString + "\"";
        txid = PublicMethed.triggerContract(contractAddress,
            "triggerStringEventAnonymous(string,string)", param, false,
            0L, 100000000L, event001Address, event001Key, blockingStubFull);
        logger.info(txid);

        txid = PublicMethed.triggerContract(contractAddress,
            "triggerStringEvent(string,string)", param, false,
            0L, 100000000L, event001Address, event001Key, blockingStubFull);
        logger.info(txid);

      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }


    }

    contractName = "addressDemo";
    code = Configuration.getByPath("testng.conf")
        .getString("code.code_ContractEventAndLog2");
    abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_ContractEventAndLog2");
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 50, null, event001Key, event001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getAbi() != null);

    txid = PublicMethed.triggerContract(contractAddress,
        "triggerEventBytes()", "#", false,
        0, maxFeeLimit, event001Address, event001Key, blockingStubFull);
    logger.info(txid);


  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


