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
import io.midasprotocol.protos.Protocol;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
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
import io.midasprotocol.protos.Protocol.Transaction.Raw;

import java.io.File;

/**
 * pragma solidity ^0.4.24;
 * <p>
 * contract ForI{
 * <p>
 * uint256 public balances;
 * <p>
 * function setCoin(uint receiver) public { for(uint i=0;i<receiver;i++){ balances = balances++; } }
 * }
 */
public class BandWidthRuntimeTest {

	public static final long totalBalance = 100_000_0000_000_000L;
	private static String dbPath = "output_BandWidthRuntimeTest_test";
	private static String dbDirectory = "db_BandWidthRuntimeTest_test";
	private static String indexDirectory = "index_BandWidthRuntimeTest_test";
	private static AnnotationConfigApplicationContext context;
	private static Manager dbManager;

	private static String OwnerAddress = "MJsKN8eHy6rH19B688QfKNfnhTDJUc1mX8";
	private static String TriggerOwnerAddress = "MRBGoSSnzSfrqvFJkaP11B3WockyiDknoU";
	private static String TriggerOwnerTwoAddress = "MN12GTFGMTbqyhRiYpDRyoVFd1Mxbtsn9N";
	private static byte[] contractAddress;

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
	public static void init() throws ContractExeException, ReceiptCheckErrException, VMIllegalException, ContractValidateException {
		dbManager = context.getBean(Manager.class);
		//init energy
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526547838000L);
		dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(10_000_000L);

		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);

		AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
				ByteString.copyFrom(Wallet.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
				totalBalance);

		accountCapsule.setFrozenForEnergy(1_000_000_000L, 0L);
		dbManager.getAccountStore()
				.put(Wallet.decodeFromBase58Check(OwnerAddress), accountCapsule);

		AccountCapsule accountCapsule2 = new AccountCapsule(
				ByteString.copyFrom("triggerOwner".getBytes()),
				ByteString.copyFrom(Wallet.decodeFromBase58Check(TriggerOwnerAddress)), AccountType.Normal,
				totalBalance);

		accountCapsule2.setFrozenForEnergy(1_000_000_000L, 0L);
		dbManager.getAccountStore()
				.put(Wallet.decodeFromBase58Check(TriggerOwnerAddress), accountCapsule2);
		AccountCapsule accountCapsule3 = new AccountCapsule(
				ByteString.copyFrom("triggerOwnerAddress".getBytes()),
				ByteString.copyFrom(Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress)),
				AccountType.Normal,
				totalBalance);
		accountCapsule3.setBandwidthUsage(10000L);
		accountCapsule3.setLatestFreeBandwidthConsumeTime(dbManager.getWitnessController().getHeadSlot());
		accountCapsule3.setFrozenForEnergy(1_000_000_000L, 0L);
		dbManager.getAccountStore()
				.put(Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress), accountCapsule3);

		dbManager.getDynamicPropertiesStore()
				.saveLatestBlockHeaderTimestamp(System.currentTimeMillis() / 1000);

		contractAddress = createContract();
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
			TriggerSmartContract triggerContract = TVMTestUtils.createTriggerContract(contractAddress,
					"setCoin(uint256)", "3", false,
					0, Wallet.decodeFromBase58Check(TriggerOwnerAddress));
			Transaction transaction = Transaction.newBuilder().setRawData(Raw.newBuilder().addContract(
					Contract.newBuilder().setParameter(Any.pack(triggerContract))
							.setType(ContractType.TriggerSmartContract)).setFeeLimit(10000000000L)).build();
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

			AccountCapsule triggerOwner = dbManager.getAccountStore()
					.get(Wallet.decodeFromBase58Check(TriggerOwnerAddress));
			long energy = triggerOwner.getEnergyUsage();
			long balance = triggerOwner.getBalance();
			Assert.assertEquals(45706, trace.getReceipt().getEnergyUsageTotal());
			Assert.assertEquals(45706, energy);
			Assert.assertEquals(totalBalance, balance);
		} catch (TronException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testSuccessNoBandwidth() {
		try {
			TriggerSmartContract triggerContract = TVMTestUtils.createTriggerContract(contractAddress,
					"setCoin(uint256)", "50", false,
					0, Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress));
			Transaction transaction = Transaction.newBuilder().setRawData(Raw.newBuilder().addContract(
					Contract.newBuilder().setParameter(Any.pack(triggerContract))
							.setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000)).build();
			TransactionCapsule trxCap = new TransactionCapsule(transaction);
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

			AccountCapsule triggerOwnerTwo = dbManager.getAccountStore()
					.get(Wallet.decodeFromBase58Check(TriggerOwnerTwoAddress));
			long balance = triggerOwnerTwo.getBalance();
			ReceiptCapsule receipt = trace.getReceipt();

			Assert.assertEquals(bandWidth, receipt.getBandwidthUsage());
			Assert.assertEquals(522850, receipt.getEnergyUsageTotal());
			Assert.assertEquals(50000, receipt.getEnergyUsage());
			Assert.assertEquals(472850000, receipt.getEnergyFee());
			Assert.assertEquals(totalBalance - receipt.getEnergyFee(),
					balance);
		} catch (TronException e) {
			Assert.fail(e.getMessage());
		}
	}

	private static byte[] createContract()
		throws ContractValidateException, ContractExeException, VMIllegalException, ReceiptCheckErrException {
		String contractName = "foriContract";
		String code = "608060405234801561001057600080fd5b50610105806100206000396000f3006080604052600436106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680637bb98a6814604e578063866edb47146076575b600080fd5b348015605957600080fd5b50606060a0565b6040518082815260200191505060405180910390f35b348015608157600080fd5b50609e6004803603810190808035906020019092919050505060a6565b005b60005481565b60008090505b8181101560d55760008081548092919060010191905055600081905550808060010191505060ac565b50505600a165627a7a72305820f4020a69fb8504d7db776726b19e5101c3216413d7ab8e91a11c4f55f772caed0029";
		String abi = "[{\"constant\":true,\"inputs\":[],\"name\":\"balances\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";

		Protocol.Transaction trx = TVMTestUtils.generateDeploySmartContractAndGetTransaction(
			contractName, Wallet.decodeFromBase58Check(OwnerAddress), abi, code, 0, Constant.CREATOR_DEFAULT_ENERGY_LIMIT, 100, null);
		byte[] contractAddress = Wallet.generateContractAddress(trx);
		DepositImpl rootDeposit = DepositImpl.createRoot(dbManager);
		Runtime runtime = TVMTestUtils.processTransactionAndReturnRuntime(trx, rootDeposit, null);
		org.testng.Assert.assertNull(runtime.getRuntimeError());
		return contractAddress;
	}
}