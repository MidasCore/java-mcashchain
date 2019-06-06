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

package io.midasprotocol.common.runtime.vm;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.runtime.Runtime;
import io.midasprotocol.common.runtime.RuntimeImpl;
import io.midasprotocol.common.runtime.TVMTestUtils;
import io.midasprotocol.common.runtime.vm.program.invoke.ProgramInvokeFactoryImpl;
import io.midasprotocol.common.storage.DepositImpl;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.capsule.ReceiptCapsule;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.db.TransactionTrace;
import io.midasprotocol.core.exception.*;
import io.midasprotocol.protos.Contract.CreateSmartContract;
import io.midasprotocol.protos.Contract.TriggerSmartContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.Transaction.Contract;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;
import io.midasprotocol.protos.Protocol.Transaction.Result.contractResult;
import io.midasprotocol.protos.Protocol.Transaction.raw;

import java.io.File;

/**
 * pragma solidity ^0.4.2;
 * <p>
 * contract Fibonacci {
 * <p>
 * event Notify(uint input, uint result);
 * <p>
 * function fibonacci(uint number) constant returns(uint result) { if (number == 0) { return 0; }
 * else if (number == 1) { return 1; } else { uint256 first = 0; uint256 second = 1; uint256 ret =
 * 0; for(uint256 i = 2; i <= number; i++) { ret = first + second; first = second; second = ret; }
 * return ret; } }
 * <p>
 * function fibonacciNotify(uint number) returns(uint result) { result = fibonacci(number);
 * Notify(number, result); } }
 */
public class BandWidthRuntimeWithCheckTest {

	public static final long totalBalance = 1000_0000_000_000L;
	private static String dbPath = "output_BandWidthRuntimeTest_test";
	private static String dbDirectory = "db_BandWidthRuntimeTest_test";
	private static String indexDirectory = "index_BandWidthRuntimeTest_test";
	private static AnnotationConfigApplicationContext context;
	private static Manager dbManager;

	private static String OwnerAddress = "MJsKN8eHy6rH19B688QfKNfnhTDJUc1mX8";
	private static String TriggerOwnerAddress = "MRBGoSSnzSfrqvFJkaP11B3WockyiDknoU";
	private static String TriggerOwnerTwoAddress = "MN12GTFGMTbqyhRiYpDRyoVFd1Mxbtsn9N";

	static {
		Args.setParam(
				new String[]{
						"--output-directory", dbPath,
						"--storage-db-directory", dbDirectory,
						"--storage-index-directory", indexDirectory,
						"-w"
				},
//				"config-test-mainnet.conf"
				Constant.TEST_CONF
		);
		context = new ApplicationContext(DefaultConfig.class);
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		//init energy
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(10_000_000L);

		AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
				ByteString.copyFrom(Wallet.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
				totalBalance);

		accountCapsule.setFrozenForEnergy(10_000_000L, 0L);
		dbManager.getAccountStore()
				.put(Wallet.decodeFromBase58Check(OwnerAddress), accountCapsule);

		AccountCapsule accountCapsule2 = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
				ByteString.copyFrom(Wallet.decodeFromBase58Check(TriggerOwnerAddress)), AccountType.Normal,
				totalBalance);

		accountCapsule2.setFrozenForEnergy(10_000_000L, 0L);
		dbManager.getAccountStore()
				.put(Wallet.decodeFromBase58Check(TriggerOwnerAddress), accountCapsule2);
		AccountCapsule accountCapsule3 = new AccountCapsule(
				ByteString.copyFrom("triggerOwnerAddress".getBytes()),
				ByteString.copyFrom(Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress)),
				AccountType.Normal,
				totalBalance);
		accountCapsule3.setNetUsage(5000L);
		accountCapsule3.setLatestConsumeFreeTime(dbManager.getWitnessController().getHeadSlot());
		accountCapsule3.setFrozenForEnergy(10_000_000L, 0L);
		dbManager.getAccountStore()
				.put(Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress), accountCapsule3);

	}

	/**
	 * destroy clear data of testing.
	 */
	@AfterClass
	public static void destroy() {
		Args.clearParam();
		ApplicationFactory.create(context).shutdown();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Test
	public void testSuccess() {
		try {
			byte[] contractAddress = createContract();
			AccountCapsule triggerOwner = dbManager.getAccountStore()
					.get(Wallet.decodeFromBase58Check(TriggerOwnerAddress));
			long energy = triggerOwner.getEnergyUsage();
			long balance = triggerOwner.getBalance();
			TriggerSmartContract triggerContract = TVMTestUtils.createTriggerContract(contractAddress,
					"fibonacciNotify(uint256)", "7000", false,
					0, Wallet.decodeFromBase58Check(TriggerOwnerAddress));
			Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
					Contract.newBuilder().setParameter(Any.pack(triggerContract))
							.setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000)).build();
			TransactionCapsule trxCap = new TransactionCapsule(transaction);
			TransactionTrace trace = new TransactionTrace(trxCap, dbManager);
			dbManager.consumeBandwidth(trxCap, trace);
			BlockCapsule blockCapsule = null;
			DepositImpl deposit = DepositImpl.createRoot(dbManager);
			Runtime runtime = new RuntimeImpl(trace, blockCapsule, deposit,
					new ProgramInvokeFactoryImpl());
			trace.init(blockCapsule);
			trace.exec();
			trace.finalization();

			triggerOwner = dbManager.getAccountStore()
					.get(Wallet.decodeFromBase58Check(TriggerOwnerAddress));
			energy = triggerOwner.getEnergyUsage() - energy;
			balance = balance - triggerOwner.getBalance();
			Assert.assertEquals(624668, trace.getReceipt().getEnergyUsageTotal());
			Assert.assertEquals(50000, energy);
			Assert.assertEquals(57466800, balance);
			Assert.assertEquals(624668 * Constant.MATOSHI_PER_ENERGY,
					balance + energy * Constant.MATOSHI_PER_ENERGY);
		} catch (TronException e) {
			Assert.assertNotNull(e);
		} catch (ReceiptCheckErrException e) {
			Assert.assertNotNull(e);
		}

	}

	@Test
	public void testSuccessNoBandWidth() {
		try {
			byte[] contractAddress = createContract();
			TriggerSmartContract triggerContract = TVMTestUtils.createTriggerContract(contractAddress,
					"fibonacciNotify(uint256)", "50", false,
					0, Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress));
			Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
					Contract.newBuilder().setParameter(Any.pack(triggerContract))
							.setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000)).build();
			TransactionCapsule trxCap = new TransactionCapsule(transaction);
			trxCap.setResultCode(contractResult.OK);
			TransactionTrace trace = new TransactionTrace(trxCap, dbManager);
			dbManager.consumeBandwidth(trxCap, trace);
			long bandWidth = trxCap.getSerializedSize() + Constant.MAX_RESULT_SIZE_IN_TX;
			BlockCapsule blockCapsule = null;
			DepositImpl deposit = DepositImpl.createRoot(dbManager);
			Runtime runtime = new RuntimeImpl(trace, blockCapsule, deposit,
					new ProgramInvokeFactoryImpl());
			trace.init(blockCapsule);
			trace.exec();
			trace.finalization();
			trace.check();
			AccountCapsule triggerOwnerTwo = dbManager.getAccountStore()
					.get(Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress));
			long balance = triggerOwnerTwo.getBalance();
			ReceiptCapsule receipt = trace.getReceipt();
			Assert.assertNull(runtime.getRuntimeError());
			Assert.assertEquals(bandWidth, receipt.getNetUsage());
			Assert.assertEquals(6118, receipt.getEnergyUsageTotal());
			Assert.assertEquals(6118, receipt.getEnergyUsage());
			Assert.assertEquals(0, receipt.getEnergyFee());
			Assert.assertEquals(totalBalance,
					balance);
		} catch (TronException e) {
			Assert.assertNotNull(e);
		} catch (ReceiptCheckErrException e) {
			Assert.assertNotNull(e);
		}
	}

	private byte[] createContract()
			throws ContractValidateException, AccountResourceInsufficientException, TooBigTransactionResultException, ContractExeException, ReceiptCheckErrException, VMIllegalException {
		AccountCapsule owner = dbManager.getAccountStore()
				.get(Wallet.decodeFromBase58Check(OwnerAddress));
		long energy = owner.getEnergyUsage();
		long balance = owner.getBalance();

		String contractName = "Fibonacci";
		String code = "608060405234801561001057600080fd5b506101ba806100206000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633c7fdc701461005157806361047ff414610092575b600080fd5b34801561005d57600080fd5b5061007c600480360381019080803590602001909291905050506100d3565b6040518082815260200191505060405180910390f35b34801561009e57600080fd5b506100bd60048036038101908080359060200190929190505050610124565b6040518082815260200191505060405180910390f35b60006100de82610124565b90507f71e71a8458267085d5ab16980fd5f114d2d37f232479c245d523ce8d23ca40ed8282604051808381526020018281526020019250505060405180910390a1919050565b60008060008060008086141561013d5760009450610185565b600186141561014f5760019450610185565b600093506001925060009150600290505b85811115156101815782840191508293508192508080600101915050610160565b8194505b505050509190505600a165627a7a7230582071f3cf655137ce9dc32d3307fb879e65f3960769282e6e452a5f0023ea046ed20029";
		String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"number\",\"type\":\"uint256\"}],\"name\":\"fibonacciNotify\",\"outputs\":[{\"name\":\"result\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"number\",\"type\":\"uint256\"}],\"name\":\"fibonacci\",\"outputs\":[{\"name\":\"result\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"input\",\"type\":\"uint256\"},{\"indexed\":false,\"name\":\"result\",\"type\":\"uint256\"}],\"name\":\"Notify\",\"type\":\"event\"}]";
		CreateSmartContract smartContract = TVMTestUtils.createSmartContract(
				Wallet.decodeFromBase58Check(OwnerAddress), contractName, abi, code, 0, 100, Constant.CREATOR_DEFAULT_ENERGY_LIMIT);
		Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
				Contract.newBuilder().setParameter(Any.pack(smartContract))
						.setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)).build();
		TransactionCapsule trxCap = new TransactionCapsule(transaction);
		trxCap.setResultCode(contractResult.OK);
		TransactionTrace trace = new TransactionTrace(trxCap, dbManager);
		dbManager.consumeBandwidth(trxCap, trace);
		BlockCapsule blockCapsule = null;
		DepositImpl deposit = DepositImpl.createRoot(dbManager);
		Runtime runtime = new RuntimeImpl(trace, blockCapsule, deposit, new ProgramInvokeFactoryImpl());
		trace.init(blockCapsule);
		trace.exec();
		trace.finalization();
		trace.check();

		owner = dbManager.getAccountStore()
				.get(Wallet.decodeFromBase58Check(OwnerAddress));
		energy = owner.getEnergyUsage() - energy;
		balance = balance - owner.getBalance();
		Assert.assertNull(runtime.getRuntimeError());
		Assert.assertEquals(88529, trace.getReceipt().getEnergyUsageTotal());
		Assert.assertEquals(50000, energy);
		Assert.assertEquals(3852900, balance);
		Assert
				.assertEquals(88529 * Constant.MATOSHI_PER_ENERGY, balance + energy * Constant.MATOSHI_PER_ENERGY);
		if (runtime.getRuntimeError() != null) {
			return runtime.getResult().getContractAddress();
		}
		return runtime.getResult().getContractAddress();

	}
}