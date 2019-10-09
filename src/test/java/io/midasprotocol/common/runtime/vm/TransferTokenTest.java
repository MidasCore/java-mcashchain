package io.midasprotocol.common.runtime.vm;

import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import io.netty.util.internal.StringUtil;
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
	 * contract tokenTest {
	 *     constructor() public payable {
	 *     }
	 *     // positive case function
	 *     function TransferTokenTo(address toAddress, token id, uint256 amount) public payable {
	 *         toAddress.transferToken(amount, id);
	 *     }
	 *     function suicide(address toAddress) payable public {
	 *         selfdestruct(toAddress);
	 *     }
	 *     function get(token trc) public payable returns (uint256) {
	 *         return address(this).tokenBalance(trc);
	 *     }
	 * }
	 *
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

		String selectorStr = "TransferTokenTo(address,token,uint256)";
		String params = "000000000000000000000000548794500882809695a8a687866e76d4271a1abc" +
				Hex.toHexString(new DataWord(id).getData()) +
				"0000000000000000000000000000000000000000000000000000000000000009"; //TRANSFER_TO, 100001, 9
		byte[] triggerData = TVMTestUtils.parseAbi(selectorStr, params);
		logger.info(StringUtil.toHexString(triggerData));

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
				"6080604052610238806100136000396000f300608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680636e81f26a1461005c5780637802dde2146100a6578063dbc1f226146100da575b600080fd5b6100a4600480360381019080803573ffffffffffffffffffffffffffffffffffffffff1690602001909291908035906020019092919080359060200190929190505050610110565b005b6100c460048036038101908080359060200190929190505050610198565b6040518082815260200191505060405180910390f35b61010e600480360381019080803573ffffffffffffffffffffffffffffffffffffffff1690602001909291905050506101f3565b005b8273ffffffffffffffffffffffffffffffffffffffff166108fc829081150290848015801561013e57600080fd5b50806780000000000000001115801561015657600080fd5b5080620f42401015801561016957600080fd5b5060405160006040518083038185878a8ad0945050505050158015610192573d6000803e3d6000fd5b50505050565b60003073ffffffffffffffffffffffffffffffffffffffff1682801580156101bf57600080fd5b5080678000000000000000111580156101d757600080fd5b5080620f4240101580156101ea57600080fd5b50d19050919050565b8073ffffffffffffffffffffffffffffffffffffffff16ff00a165627a7a72305820dac05a454c6500afa9226cdbbaff126e642f24cb70bccdf659971829d61536e30029";

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
	 * contract tokenPerformanceTest {
	 *     uint256 public counter = 0;
	 *     constructor() public payable {
	 *     }
	 *     //positive case
	 *     function TransferTokenTo(address toAddress, token id, uint256 amount) public payable {
	 *         while (true) {
	 *             counter++;
	 *             toAddress.transferToken(amount, id);
	 *         }
	 *     }
	 * }
	 */
	@Test
	public void TransferTokenSingleInstructionTimeTest()
			throws ContractExeException, ReceiptCheckErrException, VMIllegalException, ContractValidateException {
		long id = createAsset("testPerformanceToken");
		byte[] contractAddress = deployTransferTokenPerformanceContract(id);
		long triggerCallValue = 100000;
		long feeLimit = 100_000_000_000L;
		long tokenValue = 0;
		String selectorStr = "trans(address,token,uint256)";
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
				"6080604052600080556101b8806100176000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806361bc221a146100515780636e81f26a14610096575b600080fd5b34801561005d57600080fd5b50d3801561006a57600080fd5b50d2801561007757600080fd5b506100806100e0565b6040518082815260200191505060405180910390f35b6100de600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919080359060200190929190803590602001909291905050506100e6565b005b60005481565b5b6001156101875760008081548092919060010191905055508273ffffffffffffffffffffffffffffffffffffffff166108fc829081150290848015801561012d57600080fd5b50806780000000000000001115801561014557600080fd5b5080620f42401015801561015857600080fd5b5060405160006040518083038185878a8ad0945050505050158015610181573d6000803e3d6000fd5b506100e7565b5050505600a165627a7a72305820bf4ba6ad0900ddbf8e70a4434edf346989be20a0d34bfe9c79c009e9d1c1c4050029";

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
