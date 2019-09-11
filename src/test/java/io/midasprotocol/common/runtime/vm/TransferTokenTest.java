package io.midasprotocol.common.runtime.vm;

import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.runtime.Runtime;
import io.midasprotocol.common.runtime.TVMTestUtils;
import io.midasprotocol.common.runtime.config.VMConfig;
import io.midasprotocol.common.storage.DepositImpl;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.ReceiptCheckErrException;
import io.midasprotocol.core.exception.VMIllegalException;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction;

import java.io.File;

@Slf4j
public class TransferTokenTest {

	private static final String dbPath = "output_TransferTokenTest";
	private static final String OWNER_ADDRESS;
	private static final String TRANSFER_TO;
	private static final long TOTAL_SUPPLY = 1000_000_000L;
	private static final int TRX_NUM = 10;
	private static final int NUM = 1;
	private static final long START_TIME = 1;
	private static final long END_TIME = 2;
	private static final int VOTE_SCORE = 2;
	private static final String DESCRIPTION = "TRX";
	private static final String URL = "https://tron.network";
	private static Runtime runtime;
	private static Manager dbManager;
	private static ApplicationContext context;
	private static Application appT;
	private static DepositImpl deposit;
	private static AccountCapsule ownerCapsule;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		appT = ApplicationFactory.create(context);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
		TRANSFER_TO = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		dbManager = context.getBean(Manager.class);
		deposit = DepositImpl.createRoot(dbManager);
		deposit.createAccount(Hex.decode(TRANSFER_TO), AccountType.Normal);
		deposit.addBalance(Hex.decode(TRANSFER_TO), 10);
		deposit.commit();
		ownerCapsule =
				new AccountCapsule(
						ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
						ByteString.copyFromUtf8("owner"),
						AccountType.AssetIssue);

		ownerCapsule.setBalance(100_000_1000_1000L);
	}

	/**
	 * Release resources.
	 */
	@AfterClass
	public static void destroy() {
		Args.clearParam();
		appT.shutdownServices();
		appT.shutdown();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	private long createAsset(String tokenName) {
		VMConfig.initAllowVmTransferM1(1);
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
				AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFrom(ByteArray.fromString(tokenName)))
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

		ownerCapsule.addAsset(id, 100_000_000);
		dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
		return id;
	}

	/**
	 * pragma solidity ^0.4.24;
	 * <p>
	 * contract tokenTest{ constructor() public payable{} // positive case function
	 * TransferTokenTo(address toAddress, trcToken id,uint256 amount) public payable{
	 * toAddress.transferToken(amount,id); } function suicide(address toAddress) payable public{
	 * selfdestruct(toAddress); } function get(trcToken trc) public payable returns(uint256){ return
	 * address(this).tokenBalance(trc); } }
	 * <p>
	 * 1. deploy 2. trigger and internal transaction 3. suicide (all token)
	 */
	@Test
	public void TransferTokenTest()
			throws ContractExeException, ReceiptCheckErrException, VMIllegalException, ContractValidateException {
		/*  1. Test deploy with tokenValue and tokenId */
		long id = createAsset("testToken1");
		byte[] contractAddress = deployTransferTokenContract(id);
		deposit.commit();
		Assert.assertEquals(100,
				dbManager.getAccountStore().get(contractAddress).getAssetMap().get(id)
						.longValue());
		Assert.assertEquals(1000, dbManager.getAccountStore().get(contractAddress).getBalance());

		String selectorStr = "TransferTokenTo(address,trcToken,uint256)";
		String params = "000000000000000000000000548794500882809695a8a687866e76d4271a1abc" +
				Hex.toHexString(new DataWord(id).getData()) +
				"0000000000000000000000000000000000000000000000000000000000000009"; //TRANSFER_TO, 100001, 9
		byte[] triggerData = TVMTestUtils.parseAbi(selectorStr, params);

		/*  2. Test trigger with tokenValue and tokenId, also test internal transaction transferToken function */
		long triggerCallValue = 10000;
		long feeLimit = 100_000_000_000L;
		long tokenValue = 8;
		Transaction transaction = TVMTestUtils
				.generateTriggerSmartContractAndGetTransaction(Hex.decode(OWNER_ADDRESS), contractAddress,
						triggerData,
						triggerCallValue, feeLimit, tokenValue, id);
		runtime = TVMTestUtils.processTransactionAndReturnRuntime(transaction, dbManager, null);

		org.testng.Assert.assertNull(runtime.getRuntimeError());
		Assert.assertEquals(100 + tokenValue - 9,
				dbManager.getAccountStore().get(contractAddress).getAssetMap().get(id)
						.longValue());
		Assert.assertEquals(9, dbManager.getAccountStore().get(Hex.decode(TRANSFER_TO)).getAssetMap()
				.get(id).longValue());

		/*   suicide test  */
		// create new token: testToken2
		long id2 = createAsset("testToken2");
		// add token balance for last created contract
		AccountCapsule changeAccountCapsule = dbManager.getAccountStore().get(contractAddress);
		changeAccountCapsule.addAssetAmount(id2, 99);
		dbManager.getAccountStore().put(contractAddress, changeAccountCapsule);
		String selectorStr2 = "suicide(address)";
		String params2 = "000000000000000000000000548794500882809695a8a687866e76d4271a1abc"; //TRANSFER_TO
		byte[] triggerData2 = TVMTestUtils.parseAbi(selectorStr2, params2);
		Transaction transaction2 = TVMTestUtils
				.generateTriggerSmartContractAndGetTransaction(Hex.decode(OWNER_ADDRESS), contractAddress,
						triggerData2,
						triggerCallValue, feeLimit, 0, id);
		runtime = TVMTestUtils.processTransactionAndReturnRuntime(transaction2, dbManager, null);
		org.testng.Assert.assertNull(runtime.getRuntimeError());
		Assert.assertEquals(100 + tokenValue - 9 + 9,
				dbManager.getAccountStore().get(Hex.decode(TRANSFER_TO)).getAssetMap()
						.get(id).longValue());
		Assert.assertEquals(99, dbManager.getAccountStore().get(Hex.decode(TRANSFER_TO)).getAssetMap()
				.get(id2).longValue());
	}

	private byte[] deployTransferTokenContract(long id)
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {
		String contractName = "TransferWhenDeployContract";
		byte[] address = Hex.decode(OWNER_ADDRESS);
		String ABI =
				"[]";
		String code =
				"608060405261015a806100136000396000f3006080604052600436106100565763ffffffff7c0100000000000"
						+ "0000000000000000000000000000000000000000000006000350416633be9ece7811461005b578063a1"
						+ "24249714610084578063dbc1f226146100a1575b600080fd5b61008273fffffffffffffffffffffffff"
						+ "fffffffffffffff600435166024356044356100c2565b005b61008f60043561010f565b604080519182"
						+ "52519081900360200190f35b61008273ffffffffffffffffffffffffffffffffffffffff60043516610"
						+ "115565b60405173ffffffffffffffffffffffffffffffffffffffff84169082156108fc029083908590"
						+ "600081818185878a8ad0945050505050158015610109573d6000803e3d6000fd5b50505050565b3090d"
						+ "190565b8073ffffffffffffffffffffffffffffffffffffffff16ff00a165627a7a72305820c62df6f4"
						+ "5add5e57b59db51d6f6ab609564554aed5e9c958621f9c5e085a510b0029";

		long value = 1000;
		long feeLimit = 10000000000L;
		long consumeUserResourcePercent = 0;
		long tokenValue = 100;
		long tokenId = id;

		byte[] contractAddress = TVMTestUtils
				.deployContractWholeProcessReturnContractAddress(contractName, address, ABI, code, value,
						feeLimit, consumeUserResourcePercent, null, tokenValue, tokenId,
						deposit, null);
		return contractAddress;
	}

	/**
	 * contract tokenPerformanceTest{ uint256 public counter = 0; constructor() public payable{} //
	 * positive case function TransferTokenTo(address toAddress, trcToken id,uint256 amount) public
	 * payable{ while(true){ counter++; toAddress.transferToken(amount,id); } } }
	 */
	@Test
	public void TransferTokenSingleInstructionTimeTest()
			throws ContractExeException, ReceiptCheckErrException, VMIllegalException, ContractValidateException {
		long id = createAsset("testPerformanceToken");
		byte[] contractAddress = deployTransferTokenPerformanceContract(id);
		long triggerCallValue = 100000;
		long feeLimit = 100_000_000_000L;
		long tokenValue = 0;
		String selectorStr = "trans(address,trcToken,uint256)";
		String params = "000000000000000000000000548794500882809695a8a687866e76d4271a1abc" +
				Hex.toHexString(new DataWord(id).getData()) +
				"0000000000000000000000000000000000000000000000000000000000000002"; //TRANSFER_TO, 100001, 9
		byte[] triggerData = TVMTestUtils.parseAbi(selectorStr, params);
		Transaction transaction = TVMTestUtils
				.generateTriggerSmartContractAndGetTransaction(Hex.decode(OWNER_ADDRESS), contractAddress,
						triggerData,
						triggerCallValue, feeLimit, tokenValue, id);
		long start = System.nanoTime() / 1000;

		runtime = TVMTestUtils.processTransactionAndReturnRuntime(transaction, dbManager, null);
		long end = System.nanoTime() / 1000;
		System.err.println("running time:" + (end - start));
		Assert.assertTrue((end - start) < 50_0000);

	}

	private byte[] deployTransferTokenPerformanceContract(long id)
			throws ContractExeException, ReceiptCheckErrException, ContractValidateException, VMIllegalException {
		String contractName = "TransferTokenPerformanceContract";
		byte[] address = Hex.decode(OWNER_ADDRESS);
		String ABI =
				"[]";
		String code =
				"608060405260f0806100126000396000f300608060405260043610603e5763ffffffff7c01000000000000000000000"
						+ "0000000000000000000000000000000000060003504166385d73c0a81146043575b600080fd5b606873ffffff"
						+ "ffffffffffffffffffffffffffffffffff60043516602435604435606a565b005b60005b8181101560be57604"
						+ "05173ffffffffffffffffffffffffffffffffffffffff85169060009060019086908381818185878a84d09450"
						+ "5050505015801560b6573d6000803e3d6000fd5b50600101606d565b505050505600a165627a7a7230582047d"
						+ "6ab00891da9d46ef58e3d5709bac950887f450e3493518219f47829b474350029";

		long value = 100000;
		long feeLimit = 10000000000L;
		long consumeUserResourcePercent = 0;
		long tokenValue = 1000_000;
		long tokenId = id;

		byte[] contractAddress = TVMTestUtils
				.deployContractWholeProcessReturnContractAddress(contractName, address, ABI, code, value,
						feeLimit, consumeUserResourcePercent, null, tokenValue, tokenId,
						deposit, null);
		return contractAddress;
	}
}
