package stest.tron.wallet.dailybuild.trctoken;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.midasprotocol.api.GrpcAPI.AccountResourceMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractTrcToken036 {


	private static final long now = System.currentTimeMillis();
	private static final long TotalSupply = 10000000L;
	private static long assetAccountId = 0;
	private static String tokenName = "testAssetIssue_" + now;
	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key2");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	byte[] transferTokenContractAddress;
	String description = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetDescription");
	String url = Configuration.getByPath("testng.conf")
			.getString("defaultParameter.assetUrl");
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] dev001Address = ecKey1.getAddress();
	String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	ECKey ecKey2 = new ECKey(Utils.getRandom());
	byte[] user001Address = ecKey2.getAddress();
	String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
	int originEnergyLimit = 50000;
	byte[] transferTokenWithPureTestAddress;
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private Long maxFeeLimit = Configuration.getByPath("testng.conf")
			.getLong("defaultParameter.maxFeeLimit");

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

	}


	@Test(enabled = true, description = "Deploy contract")
	public void deploy01TransferTokenContract() {

		Assert
				.assertTrue(PublicMethed.sendcoin(dev001Address, 9999000000L, fromAddress,
						testKey002, blockingStubFull));
		logger.info(
				"dev001Address:" + Base58.encodeBase58(dev001Address));
		Assert
				.assertTrue(PublicMethed.sendcoin(user001Address, 4048000000L, fromAddress,
						testKey002, blockingStubFull));
		logger.info(
				"user001Address:" + Base58.encodeBase58(user001Address));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// freeze balance
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(dev001Address, 204800000,
				0, 1, dev001Key, blockingStubFull));

		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(user001Address, 2048000000,
				0, 1, user001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		long start = System.currentTimeMillis() + 2000;
		long end = System.currentTimeMillis() + 1000000000;
		//Create a new AssetIssue success.
		Assert.assertTrue(PublicMethed.createAssetIssue(dev001Address, tokenName, TotalSupply, 1,
				100, start, end, 1, description, url, 10000L,
				10000L, 1L, 1L, dev001Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedId();

		// deploy transferTokenContract
		String contractName = "tokenTest";
		String code = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken036_tokenTest");
		String abi = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken036_tokenTest");
		transferTokenContractAddress = PublicMethed
				.deployContract(contractName, abi, code, "", maxFeeLimit,
						0L, 0, originEnergyLimit, 0,
						0, null, dev001Key, dev001Address,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// devAddress transfer token to userAddress
		PublicMethed.transferAsset(transferTokenContractAddress, assetAccountId, 100,
				dev001Address,
				dev001Key,
				blockingStubFull);
		Assert.assertTrue(PublicMethed.sendcoin(transferTokenContractAddress, 100, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
	}

	@Test(enabled = true, description = "Trigger transferTokenWithPure contract")
	public void deploy02TransferTokenContract() {
		Account info;
		AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		info = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		Long beforeBalance = info.getBalance();
		Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
		Long beforeNetUsed = resourceInfo.getBandwidthUsed();
		Long beforeFreeNetUsed = resourceInfo.getFreeBandwidthUsed();
		Long beforeAssetIssueDevAddress = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueUserAddress = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);

		Long beforeAssetIssueContractAddress = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);
		Long user001AddressAddressBalance = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();
		logger.info("beforeBalance:" + beforeBalance);
		logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
		logger.info("beforeNetUsed:" + beforeNetUsed);
		logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
		logger.info("beforeAssetIssueCount:" + beforeAssetIssueContractAddress);
		logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress);
		logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress);
		logger.info("user001AddressAddressBalance:" + user001AddressAddressBalance);

		// user trigger A to transfer token to B
		String param =
				"\"" + Base58.encodeBase58(user001Address) + "\",\"1\"";

		final String triggerTxid = PublicMethed.triggerContract(transferTokenContractAddress,
				"transferTokenWithPure(address,uint256)",
				param, false, 10, 1000000000L, assetAccountId,
				10, dev001Address, dev001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account infoafter = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		Long afterBalance = infoafter.getBalance();
		Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
		Long afterAssetIssueDevAddress = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long afterNetUsed = resourceInfoafter.getBandwidthUsed();
		Long afterFreeNetUsed = resourceInfoafter.getFreeBandwidthUsed();
		Long afterAssetIssueContractAddress = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);
		Long afterAssetIssueUserAddress = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long afteruser001AddressAddressBalance = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();

		logger.info("afterBalance:" + afterBalance);
		logger.info("afterEnergyUsed:" + afterEnergyUsed);
		logger.info("afterNetUsed:" + afterNetUsed);
		logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
		logger.info("afterAssetIssueCount:" + afterAssetIssueDevAddress);
		logger.info("afterAssetIssueDevAddress:" + afterAssetIssueContractAddress);
		logger.info("afterAssetIssueUserAddress:" + afterAssetIssueUserAddress);
		logger.info("afterContractAddressBalance:" + afteruser001AddressAddressBalance);

		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(triggerTxid, blockingStubFull);
		Assert.assertTrue(infoById.get().getResultValue() == 0);
		Assert.assertTrue(beforeAssetIssueDevAddress - 10 == afterAssetIssueDevAddress);
		Assert.assertTrue(beforeAssetIssueUserAddress + 10 == afterAssetIssueUserAddress);
		Assert.assertTrue(user001AddressAddressBalance + 10 == afteruser001AddressAddressBalance);

		String contractName1 = "transferTokenWithPureTest";
		String code1 = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken036_transferTokenWithPureTest");
		String abi1 = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken036_transferTokenWithPureTest");
		transferTokenWithPureTestAddress = PublicMethed
				.deployContract(contractName1, abi1, code1, "", maxFeeLimit,
						0L, 0, originEnergyLimit, 0,
						0, null, dev001Key, dev001Address,
						blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// devAddress transfer token to userAddress
		PublicMethed.transferAsset(transferTokenWithPureTestAddress, assetAccountId, 100,
				dev001Address,
				dev001Key,
				blockingStubFull);
		Assert.assertTrue(PublicMethed.sendcoin(transferTokenWithPureTestAddress, 100, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
	}

	@Test(enabled = true, description = "Trigger transferTokenWithConstant contract")
	public void deploy03TransferTokenContract() {
		Account info1;
		AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		info1 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		Long beforeBalance1 = info1.getBalance();
		Long beforeEnergyUsed1 = resourceInfo1.getEnergyUsed();
		Long beforeNetUsed1 = resourceInfo1.getBandwidthUsed();
		Long beforeFreeNetUsed1 = resourceInfo1.getFreeBandwidthUsed();
		Long beforeAssetIssueDevAddress1 = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueUserAddress1 = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);

		Long beforeAssetIssueContractAddress1 = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);
		Long user001AddressAddressBalance1 = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();
		logger.info("beforeBalance:" + beforeBalance1);
		logger.info("beforeEnergyUsed:" + beforeEnergyUsed1);
		logger.info("beforeNetUsed:" + beforeNetUsed1);
		logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed1);
		logger.info("beforeAssetIssueCount:" + beforeAssetIssueContractAddress1);
		logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress1);
		logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress1);
		logger.info("user001AddressAddressBalance:" + user001AddressAddressBalance1);

		// user trigger A to transfer token to B
		String param1 =
				"\"" + Base58.encodeBase58(user001Address) + "\",\"1\"";

		final String triggerTxid1 = PublicMethed.triggerContract(transferTokenWithPureTestAddress,
				"transferTokenWithConstant(address,uint256)",
				param1, false, 10, 1000000000L, assetAccountId,
				10, dev001Address, dev001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account infoafter1 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		Long afterBalance1 = infoafter1.getBalance();
		Long afterEnergyUsed1 = resourceInfoafter1.getEnergyUsed();
		Long afterAssetIssueDevAddress1 = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long afterNetUsed1 = resourceInfoafter1.getBandwidthUsed();
		Long afterFreeNetUsed1 = resourceInfoafter1.getFreeBandwidthUsed();
		Long afterAssetIssueContractAddress1 = PublicMethed
				.getAssetIssueValue(transferTokenContractAddress,
						assetAccountId,
						blockingStubFull);
		Long afterAssetIssueUserAddress1 = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long afteruser001AddressAddressBalance1 = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();

		logger.info("afterBalance:" + afterBalance1);
		logger.info("afterEnergyUsed:" + afterEnergyUsed1);
		logger.info("afterNetUsed:" + afterNetUsed1);
		logger.info("afterFreeNetUsed:" + afterFreeNetUsed1);
		logger.info("afterAssetIssueCount:" + afterAssetIssueDevAddress1);
		logger.info("afterAssetIssueDevAddress:" + afterAssetIssueContractAddress1);
		logger.info("afterAssetIssueUserAddress:" + afterAssetIssueUserAddress1);
		logger.info("afterContractAddressBalance:" + afteruser001AddressAddressBalance1);

		Optional<TransactionInfo> infoById1 = PublicMethed
				.getTransactionInfoById(triggerTxid1, blockingStubFull);
		Assert.assertEquals(beforeBalance1, afterBalance1);
		Assert.assertEquals(beforeAssetIssueDevAddress1, afterAssetIssueDevAddress1);
		Assert.assertEquals(beforeAssetIssueUserAddress1, afterAssetIssueUserAddress1);
		Assert.assertEquals(user001AddressAddressBalance1, afteruser001AddressAddressBalance1);
	}

	@Test(enabled = true, description = "Trigger transferTokenWithView contract")
	public void deploy04TransferTokenContract() {
		String contractName2 = "transferTokenWithViewTest";

		String code2 = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken036_transferTokenWithViewTest");
		String abi2 = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken036_transferTokenWithViewTest");
		byte[] transferTokenWithViewAddress = PublicMethed
				.deployContract(contractName2, abi2, code2, "", maxFeeLimit,
						0L, 0, originEnergyLimit, 0,
						0, null, dev001Key, dev001Address,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		// devAddress transfer token to userAddress
		PublicMethed.transferAsset(transferTokenWithViewAddress, assetAccountId, 100,
				dev001Address,
				dev001Key,
				blockingStubFull);
		Assert.assertTrue(PublicMethed.sendcoin(transferTokenWithViewAddress, 100, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account info2;
		AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		info2 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		Long beforeBalance2 = info2.getBalance();
		Long beforeEnergyUsed2 = resourceInfo2.getEnergyUsed();
		Long beforeNetUsed2 = resourceInfo2.getBandwidthUsed();
		Long beforeFreeNetUsed2 = resourceInfo2.getFreeBandwidthUsed();
		Long beforeAssetIssueDevAddress2 = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueUserAddress2 = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);

		Long beforeAssetIssueContractAddress2 = PublicMethed
				.getAssetIssueValue(transferTokenWithViewAddress,
						assetAccountId,
						blockingStubFull);
		Long user001AddressAddressBalance2 = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();
		logger.info("beforeAssetIssueContractAddress2:" + beforeAssetIssueContractAddress2);
		logger.info("beforeAssetIssueDevAddress2:" + beforeAssetIssueDevAddress2);
		logger.info("beforeAssetIssueUserAddress2:" + beforeAssetIssueUserAddress2);
		logger.info("user001AddressAddressBalance2:" + user001AddressAddressBalance2);

		// user trigger A to transfer token to B
		String param2 = "\"" + Base58.encodeBase58(user001Address) + "\",\"1\"";

		String triggerTxid2 = PublicMethed.triggerContract(transferTokenWithViewAddress,
				"transferTokenWithView(address,uint256)",
				param2, false, 10, 1000000000L, assetAccountId,
				10, dev001Address, dev001Key,
				blockingStubFull);

		Account infoafter2 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		AccountResourceMessage resourceInfoafter2 = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		Long afterBalance2 = infoafter2.getBalance();
		Long afterEnergyUsed2 = resourceInfoafter2.getEnergyUsed();
		Long afterAssetIssueDevAddress2 = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long afterNetUsed2 = resourceInfoafter2.getBandwidthUsed();
		Long afterFreeNetUsed2 = resourceInfoafter2.getFreeBandwidthUsed();
		Long afterAssetIssueContractAddress2 = PublicMethed
				.getAssetIssueValue(transferTokenWithViewAddress,
						assetAccountId,
						blockingStubFull);
		Long afterAssetIssueUserAddress2 = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long afteruser001AddressAddressBalance2 = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();

		logger.info("afterAssetIssueDevAddress2:" + afterAssetIssueDevAddress2);
		logger.info("afterAssetIssueContractAddress2:" + afterAssetIssueContractAddress2);
		logger.info("afterAssetIssueUserAddress2:" + afterAssetIssueUserAddress2);
		logger.info("afteruser001AddressAddressBalance2:" + afteruser001AddressAddressBalance2);

		Optional<TransactionInfo> infoById2 = PublicMethed
				.getTransactionInfoById(triggerTxid2, blockingStubFull);

		Assert.assertEquals(beforeAssetIssueDevAddress2, afterAssetIssueDevAddress2);
		Assert.assertEquals(beforeAssetIssueUserAddress2, afterAssetIssueUserAddress2);
		Assert.assertEquals(user001AddressAddressBalance2, afteruser001AddressAddressBalance2);
	}

	@Test(enabled = true, description = "Trigger transferTokenWithNoPayable contract")
	public void deploy05TransferTokenContract() {
		String contractName3 = "transferTokenWithOutPayableTest";
		String code3 = Configuration.getByPath("testng.conf")
				.getString("code.code_ContractTrcToken036_transferTokenWithOutPayableTest");
		String abi3 = Configuration.getByPath("testng.conf")
				.getString("abi.abi_ContractTrcToken036_transferTokenWithOutPayableTest");
		byte[] transferTokenWithOutPayableTestAddress = PublicMethed
				.deployContract(contractName3, abi3, code3, "", maxFeeLimit,
						0L, 0, originEnergyLimit, 0,
						0, null, dev001Key, dev001Address,
						blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		PublicMethed
				.transferAsset(transferTokenWithOutPayableTestAddress, assetAccountId, 100,
						dev001Address,
						dev001Key,
						blockingStubFull);
		Assert
				.assertTrue(PublicMethed.sendcoin(transferTokenWithOutPayableTestAddress, 100, fromAddress,
						testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Account info3;
		AccountResourceMessage resourceInfo3 = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		info3 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		Long beforeBalance3 = info3.getBalance();
		Long beforeEnergyUsed3 = resourceInfo3.getEnergyUsed();
		Long beforeNetUsed3 = resourceInfo3.getBandwidthUsed();
		Long beforeFreeNetUsed3 = resourceInfo3.getFreeBandwidthUsed();
		Long beforeAssetIssueDevAddress3 = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long beforeAssetIssueUserAddress3 = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);

		Long beforeAssetIssueContractAddress3 = PublicMethed
				.getAssetIssueValue(
						transferTokenWithOutPayableTestAddress,
						assetAccountId,
						blockingStubFull);
		Long user001AddressAddressBalance3 = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();
		logger.info("beforeBalance:" + beforeBalance3);
		logger.info("beforeEnergyUsed:" + beforeEnergyUsed3);
		logger.info("beforeNetUsed:" + beforeNetUsed3);
		logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed3);
		logger.info("beforeAssetIssueCount:" + beforeAssetIssueContractAddress3);
		logger.info("beforeAssetIssueDevAddress:" + beforeAssetIssueDevAddress3);
		logger.info("beforeAssetIssueUserAddress:" + beforeAssetIssueUserAddress3);
		logger.info("user001AddressAddressBalance:" + user001AddressAddressBalance3);

		String param3 =
				"\"" + Base58.encodeBase58(user001Address) + "\",\"1\"";

		String triggerTxid3 = PublicMethed.triggerContract(transferTokenWithOutPayableTestAddress,
				"transferTokenWithOutPayable(address,uint256)",
				param3, false, 10, 1000000000L, assetAccountId,
				10, dev001Address, dev001Key,
				blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Account infoafter3 = PublicMethed.queryAccount(dev001Address, blockingStubFull);
		AccountResourceMessage resourceInfoafter3 = PublicMethed.getAccountResource(dev001Address,
				blockingStubFull);
		Long afterBalance3 = infoafter3.getBalance();
		Long afterEnergyUsed3 = resourceInfoafter3.getEnergyUsed();
		Long afterAssetIssueDevAddress3 = PublicMethed
				.getAssetIssueValue(dev001Address, assetAccountId,
						blockingStubFull);
		Long afterNetUsed3 = resourceInfoafter3.getBandwidthUsed();
		Long afterFreeNetUsed3 = resourceInfoafter3.getFreeBandwidthUsed();
		Long afterAssetIssueContractAddress3 = PublicMethed
				.getAssetIssueValue(
						transferTokenWithOutPayableTestAddress, assetAccountId,
						blockingStubFull);
		Long afterAssetIssueUserAddress3 = PublicMethed
				.getAssetIssueValue(user001Address, assetAccountId,
						blockingStubFull);
		Long afteruser001AddressAddressBalance3 = PublicMethed
				.queryAccount(user001Address, blockingStubFull).getBalance();

		Optional<TransactionInfo> infoById3 = PublicMethed
				.getTransactionInfoById(triggerTxid3, blockingStubFull);
		Assert.assertTrue(infoById3.get().getResultValue() == 1);

		Assert.assertEquals(beforeAssetIssueDevAddress3, afterAssetIssueDevAddress3);
		Assert.assertEquals(beforeAssetIssueUserAddress3, afterAssetIssueUserAddress3);
		Assert.assertEquals(user001AddressAddressBalance3, afteruser001AddressAddressBalance3);
		PublicMethed.unFreezeBalance(dev001Address, dev001Key, 1,
				dev001Address, blockingStubFull);
		PublicMethed.unFreezeBalance(user001Address, user001Key, 1,
				user001Address, blockingStubFull);

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


