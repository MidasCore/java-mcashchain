package stest.tron.wallet.dailybuild.manual;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import io.midasprotocol.api.GrpcAPI.AccountResourceMessage;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractLinkage005 {

	private final String testKey003 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
	String contractName;
	String code;
	String abi;
	Long zeroForCycleCost;
	Long firstForCycleCost;
	Long secondForCycleCost;
	Long thirdForCycleCost;
	Long forthForCycleCost;
	Long fifthForCycleCost;
	Long zeroForCycleTimes = 498L;
	Long firstForCycleTimes = 500L;
	Long secondForCycleTimes = 502L;
	Long thirdForCycleTimes = 504L;
	Long forthForCycleTimes = 506L;
	Long fifthForCycleTimes = 508L;
	byte[] contractAddress;
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] linkage005Address = ecKey1.getAddress();
	String linkage005Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
	private ManagedChannel channelFull1 = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
	private String fullnode1 = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(1);
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
		PublicMethed.printAddress(linkage005Key);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
				.usePlaintext(true)
				.build();
		blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
	}

	@Test(enabled = true, description = "Every same trigger use same energy and net")
	public void testEnergyCostDetail() {
		PublicMethed.waitProduceNextBlock(blockingStubFull1);
		Assert.assertTrue(PublicMethed.sendcoin(linkage005Address, 5000000000000L, fromAddress,
				testKey003, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Assert.assertTrue(PublicMethed.freezeBalance(linkage005Address, 250000000000L,
				0, linkage005Key, blockingStubFull));
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(linkage005Address, 250000000000L,
				0, 1, linkage005Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);

		contractName = "EnergyCost";
		code = "6080604052600060035534801561001557600080fd5b5061027b806100256000396000f300608060405260"
				+ "0436106100825763ffffffff7c0100000000000000000000000000000000000000000000000000000000600"
				+ "0350416633755cd3c81146100875780637d965688146100b1578063a05b2577146100c9578063b0d6304d14"
				+ "6100e1578063bbe1d75b14610115578063f8a8fd6d1461012a578063fe75faab14610141575b600080fd5b3"
				+ "4801561009357600080fd5b5061009f600435610159565b60408051918252519081900360200190f35b3480"
				+ "156100bd57600080fd5b5061009f600435610178565b3480156100d557600080fd5b5061009f60043561019"
				+ "8565b3480156100ed57600080fd5b5061009f73ffffffffffffffffffffffffffffffffffffffff60043581"
				+ "1690602435166101e2565b34801561012157600080fd5b5061009f6101ff565b34801561013657600080fd5"
				+ "b5061013f610205565b005b34801561014d57600080fd5b5061009f600435610218565b6000805482908110"
				+ "61016757fe5b600091825260209091200154905081565b600080805b8381101561019157600191820191016"
				+ "1017d565b5092915050565b600080805b838110156101915760008054600181810183559180527f290decd9"
				+ "548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e56301829055918201910161019d565b600"
				+ "260209081526000928352604080842090915290825290205481565b60015481565b60038054600101905561"
				+ "0216610205565b565b60006102238261022e565b600181905592915050565b600061023c6002830361022e5"
				+ "65b6102486001840361022e565b01929150505600a165627a7a72305820bc44fd5f3a0e48cc057752b52e3a"
				+ "bf50cd7dc75b3874ea7d049893cf1a2e345f0029";
		abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":\""
				+ "iarray\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\""
				+ "stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{"
				+ "\"name\":\"a\",\"type\":\"uint256\"}],\"name\":\"testUseCpu\",\"outputs\":[{\"name\":"
				+ "\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\""
				+ "type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"a\",\"type\":\""
				+ "uint256\"}],\"name\":\"testUseStorage\",\"outputs\":[{\"name\":\"\",\"type\":\""
				+ "uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function"
				+ "\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\","
				+ "\"type\":\"address\"}],\"name\":\"m\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\""
				+ "}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\""
				+ ":true,\"inputs\":[],\"name\":\"calculatedFibNumber\",\"outputs\":[{\"name\":\"\",\"type"
				+ "\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},"
				+ "{\"constant\":false,\"inputs\":[],\"name\":\"test\",\"outputs\":[],\"payable\":false,"
				+ "\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs"
				+ "\":[{\"name\":\"n\",\"type\":\"uint256\"}],\"name\":\"setFibonacci\",\"outputs\":"
				+ "[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\""
				+ "nonpayable\",\"type\":\"function\"}]";
		AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage005Address,
				blockingStubFull);
		Account info;
		info = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
		Long beforeBalance = info.getBalance();
		Long beforeEnergyLimit = resourceInfo.getEnergyLimit();
		Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
		Long beforeFreeNetLimit = resourceInfo.getFreeBandwidthLimit();
		Long beforeNetLimit = resourceInfo.getBandwidthLimit();
		Long beforeNetUsed = resourceInfo.getBandwidthUsed();
		Long beforeFreeNetUsed = resourceInfo.getFreeBandwidthUsed();
		logger.info("beforeBalance:" + beforeBalance);
		logger.info("beforeEnergyLimit:" + beforeEnergyLimit);
		logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
		logger.info("beforeFreeNetLimit:" + beforeFreeNetLimit);
		logger.info("beforeNetLimit:" + beforeNetLimit);
		logger.info("beforeNetUsed:" + beforeNetUsed);
		logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
		String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
				"", maxFeeLimit, 0L, 100, null, linkage005Key,
				linkage005Address, blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		Optional<TransactionInfo> infoById = null;
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		Account infoafter = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
		AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage005Address,
				blockingStubFull1);
		Long afterBalance = infoafter.getBalance();
		Long afterEnergyLimit = resourceInfoafter.getEnergyLimit();
		Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
		Long afterFreeNetLimit = resourceInfoafter.getFreeBandwidthLimit();
		Long afterNetLimit = resourceInfoafter.getBandwidthLimit();
		Long afterNetUsed = resourceInfoafter.getBandwidthUsed();
		Long afterFreeNetUsed = resourceInfoafter.getFreeBandwidthUsed();
		logger.info("afterBalance:" + afterBalance);
		logger.info("afterEnergyLimit:" + afterEnergyLimit);
		logger.info("afterEnergyUsed:" + afterEnergyUsed);
		logger.info("afterFreeNetLimit:" + afterFreeNetLimit);
		logger.info("afterNetLimit:" + afterNetLimit);
		logger.info("afterNetUsed:" + afterNetUsed);
		logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
		logger.info("---------------:");
		long fee = infoById.get().getFee();

		Assert.assertTrue(beforeBalance - fee == afterBalance);
		//Assert.assertTrue(afterEnergyUsed > 0);
		//Assert.assertTrue(afterFreeNetUsed > 0);
		firstForCycleTimes = 1000L;
		secondForCycleTimes = 1002L;
		thirdForCycleTimes = 1004L;

		AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage005Address,
				blockingStubFull);
		Account info1 = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
		Long beforeBalance1 = info1.getBalance();
		Long beforeEnergyLimit1 = resourceInfo1.getEnergyLimit();
		Long beforeEnergyUsed1 = resourceInfo1.getEnergyUsed();
		Long beforeFreeNetLimit1 = resourceInfo1.getFreeBandwidthLimit();
		Long beforeNetLimit1 = resourceInfo1.getBandwidthLimit();
		Long beforeNetUsed1 = resourceInfo1.getBandwidthUsed();
		Long beforeFreeNetUsed1 = resourceInfo1.getFreeBandwidthUsed();
		logger.info("beforeBalance1:" + beforeBalance1);
		logger.info("beforeEnergyLimit1:" + beforeEnergyLimit1);
		logger.info("beforeEnergyUsed1:" + beforeEnergyUsed1);
		logger.info("beforeFreeNetLimit1:" + beforeFreeNetLimit1);
		logger.info("beforeNetLimit1:" + beforeNetLimit1);
		logger.info("beforeNetUsed1:" + beforeNetUsed1);
		logger.info("beforeFreeNetUsed1:" + beforeFreeNetUsed1);
		byte[] contractAddress = infoById.get().getContractAddress().toByteArray();
		txid = PublicMethed.triggerContract(contractAddress,
				"testUseCpu(uint256)", firstForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull1);
		Account infoafter1 = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
		AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage005Address,
				blockingStubFull1);
		Long afterBalance1 = infoafter1.getBalance();
		Long afterEnergyLimit1 = resourceInfoafter1.getEnergyLimit();
		Long afterEnergyUsed1 = resourceInfoafter1.getEnergyUsed();
		Long afterFreeNetLimit1 = resourceInfoafter1.getFreeBandwidthLimit();
		Long afterNetLimit1 = resourceInfoafter1.getBandwidthLimit();
		Long afterNetUsed1 = resourceInfoafter1.getBandwidthUsed();
		Long afterFreeNetUsed1 = resourceInfoafter1.getFreeBandwidthUsed();
		logger.info("afterBalance1:" + afterBalance1);
		logger.info("afterEnergyLimit1:" + afterEnergyLimit1);
		logger.info("afterEnergyUsed1:" + afterEnergyUsed1);
		logger.info("afterFreeNetLimit1:" + afterFreeNetLimit1);
		logger.info("afterNetLimit1:" + afterNetLimit1);
		logger.info("afterNetUsed1:" + afterNetUsed1);
		logger.info("afterFreeNetUsed1:" + afterFreeNetUsed1);
		logger.info("---------------:");
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		fee = infoById.get().getFee();
		firstForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();
		Assert.assertTrue((beforeBalance1 - fee) == afterBalance1);
		Assert.assertTrue(afterEnergyUsed1 > beforeEnergyUsed1);
		Assert.assertTrue(afterNetUsed1 > beforeNetUsed1);
		//use EnergyUsed and NetUsed.balance not change

		String txid6 = PublicMethed.triggerContract(contractAddress,
				"testUseCpu(uint256)", secondForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
		final String txid7 = PublicMethed.triggerContract(contractAddress,
				"testUseCpu(uint256)", thirdForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		infoById = PublicMethed.getTransactionInfoById(txid6, blockingStubFull);
		secondForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		infoById = PublicMethed.getTransactionInfoById(txid7, blockingStubFull);
		thirdForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		Assert.assertTrue(thirdForCycleCost - secondForCycleCost
				== secondForCycleCost - firstForCycleCost);

		zeroForCycleTimes = 498L;
		firstForCycleTimes = 500L;
		secondForCycleTimes = 502L;
		thirdForCycleTimes = 504L;
		forthForCycleTimes = 506L;
		fifthForCycleTimes = 508L;
		AccountResourceMessage resourceInfo4 = PublicMethed.getAccountResource(linkage005Address,
				blockingStubFull);
		Account info4 = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
		Long beforeBalance4 = info4.getBalance();
		Long beforeEnergyLimit4 = resourceInfo4.getEnergyLimit();
		Long beforeEnergyUsed4 = resourceInfo4.getEnergyUsed();
		Long beforeFreeNetLimit4 = resourceInfo4.getFreeBandwidthLimit();
		Long beforeNetLimit4 = resourceInfo4.getBandwidthLimit();
		Long beforeNetUsed4 = resourceInfo4.getBandwidthUsed();
		Long beforeFreeNetUsed4 = resourceInfo4.getFreeBandwidthUsed();
		logger.info("beforeBalance4:" + beforeBalance4);
		logger.info("beforeEnergyLimit4:" + beforeEnergyLimit4);
		logger.info("beforeEnergyUsed4:" + beforeEnergyUsed4);
		logger.info("beforeFreeNetLimit4:" + beforeFreeNetLimit4);
		logger.info("beforeNetLimit4:" + beforeNetLimit4);
		logger.info("beforeNetUsed4:" + beforeNetUsed4);
		logger.info("beforeFreeNetUsed4:" + beforeFreeNetUsed4);
		txid = PublicMethed.triggerContract(contractAddress,
				"testUseStorage(uint256)", zeroForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		fee = infoById.get().getFee();
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull1);
		Account infoafter4 = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
		AccountResourceMessage resourceInfoafter4 = PublicMethed.getAccountResource(linkage005Address,
				blockingStubFull1);
		Long afterBalance4 = infoafter4.getBalance();
		Long afterEnergyLimit4 = resourceInfoafter4.getEnergyLimit();
		Long afterEnergyUsed4 = resourceInfoafter4.getEnergyUsed();
		Long afterFreeNetLimit4 = resourceInfoafter4.getFreeBandwidthLimit();
		Long afterNetLimit4 = resourceInfoafter4.getBandwidthLimit();
		Long afterNetUsed4 = resourceInfoafter4.getBandwidthUsed();
		Long afterFreeNetUsed4 = resourceInfoafter4.getFreeBandwidthUsed();
		logger.info("afterBalance4:" + afterBalance4);
		logger.info("afterEnergyLimit4:" + afterEnergyLimit4);
		logger.info("afterEnergyUsed4:" + afterEnergyUsed4);
		logger.info("afterFreeNetLimit4:" + afterFreeNetLimit4);
		logger.info("afterNetLimit4:" + afterNetLimit4);
		logger.info("afterNetUsed4:" + afterNetUsed4);
		logger.info("afterFreeNetUsed4:" + afterFreeNetUsed4);
		logger.info("---------------:");
		Assert.assertTrue(beforeBalance4 - fee == afterBalance4);
		Assert.assertTrue(afterEnergyUsed4 > beforeEnergyUsed4);
		Assert.assertTrue(afterNetUsed4 > beforeNetUsed4);

		infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
		zeroForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		String txid1 = PublicMethed.triggerContract(contractAddress,
				"testUseStorage(uint256)", firstForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

		final String txid2 = PublicMethed.triggerContract(contractAddress,
				"testUseStorage(uint256)", secondForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

		final String txid3 = PublicMethed.triggerContract(contractAddress,
				"testUseStorage(uint256)", thirdForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

		final String txid4 = PublicMethed.triggerContract(contractAddress,
				"testUseStorage(uint256)", forthForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

		final String txid5 = PublicMethed.triggerContract(contractAddress,
				"testUseStorage(uint256)", fifthForCycleTimes.toString(), false,
				0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

		PublicMethed.waitProduceNextBlock(blockingStubFull);

		infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
		firstForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		infoById = PublicMethed.getTransactionInfoById(txid2, blockingStubFull);
		secondForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		infoById = PublicMethed.getTransactionInfoById(txid3, blockingStubFull);
		thirdForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		infoById = PublicMethed.getTransactionInfoById(txid4, blockingStubFull);
		forthForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		infoById = PublicMethed.getTransactionInfoById(txid5, blockingStubFull);
		fifthForCycleCost = infoById.get().getReceipt().getEnergyUsageTotal();

		Assert.assertTrue(thirdForCycleCost - secondForCycleCost
				== secondForCycleCost - firstForCycleCost);
		Assert.assertTrue(fifthForCycleCost - forthForCycleCost
				== forthForCycleCost - thirdForCycleCost);

		PublicMethed.unFreezeBalance(linkage005Address, linkage005Key, 1,
				linkage005Address, blockingStubFull);

	}

	/**
	 * constructor.
	 */

	@AfterClass
	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (channelFull1 != null) {
			channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}


}


