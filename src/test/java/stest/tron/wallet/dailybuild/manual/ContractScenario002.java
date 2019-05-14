package stest.tron.wallet.dailybuild.manual;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.PublicMethed;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContractScenario002 {

	private final String testKey002 = Configuration.getByPath("testng.conf")
			.getString("foundationAccount.key1");
	private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
	ECKey ecKey1 = new ECKey(Utils.getRandom());
	byte[] contract002Address = ecKey1.getAddress();
	String contract002Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
	private ManagedChannel channelFull = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private ManagedChannel channelFull1 = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
	private String fullnode = Configuration.getByPath("testng.conf")
			.getStringList("fullnode.ip.list").get(0);
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
		PublicMethed.printAddress(contract002Key);
		channelFull = ManagedChannelBuilder.forTarget(fullnode)
				.usePlaintext(true)
				.build();
		blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
				.usePlaintext(true)
				.build();
		blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

	}

	@Test(enabled = true, description = "Deploy contract with java-tron support interface")
	public void deployTronNative() {
		ECKey ecKey1 = new ECKey(Utils.getRandom());
		byte[] contract002Address = ecKey1.getAddress();
		String contract002Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

		Assert.assertTrue(PublicMethed.sendcoin(contract002Address, 50000000L, fromAddress,
				testKey002, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		Assert.assertTrue(PublicMethed.freezeBalanceGetEnergy(contract002Address, 1000000L,
				0, 1, contract002Key, blockingStubFull));
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract002Address,
				blockingStubFull);
		Long energyLimit = accountResource.getEnergyLimit();
		Long energyUsage = accountResource.getEnergyUsed();
		Long balanceBefore = PublicMethed.queryAccount(contract002Key, blockingStubFull).getBalance();

		logger.info("before energy limit is " + Long.toString(energyLimit));
		logger.info("before energy usage is " + Long.toString(energyUsage));
		logger.info("before balance is " + Long.toString(balanceBefore));

		String contractName = "tronNative";
		String code = "608060405260008054600160a060020a03199081166201000117909155600180548216620100021"
				+ "790556002805482166201000317905560038054821662010004179055600480548216620100051790556005"
				+ "8054821662010006179055600680549091166201000717905534801561007757600080fd5b506104ce80610"
				+ "0876000396000f3006080604052600436106100da5763ffffffff7c01000000000000000000000000000000"
				+ "000000000000000000000000006000350416630a90265081146100df5780630dfb51ac146100fc57806345b"
				+ "d20101461012d5780634efaaa1b1461014257806352ae1b811461016657806353c4263f1461017b5780635f"
				+ "d8c710146101905780637c369c90146101a55780637f2b7f93146101ba5780638259d5531461020f5780639"
				+ "06fbec914610227578063961a8be71461023c578063cee14bb414610251578063ec9928bd14610275578063"
				+ "fb4f32aa14610292575b600080fd5b3480156100eb57600080fd5b506100fa6004356024356102a7565b005"
				+ "b34801561010857600080fd5b506101116102dc565b60408051600160a060020a0390921682525190819003"
				+ "60200190f35b34801561013957600080fd5b506101116102eb565b34801561014e57600080fd5b506100fa6"
				+ "00160a060020a03600435166024356102fa565b34801561017257600080fd5b50610111610320565b348015"
				+ "61018757600080fd5b5061011161032f565b34801561019c57600080fd5b506100fa61033e565b348015610"
				+ "1b157600080fd5b5061011161035d565b3480156101c657600080fd5b506040805160206004803580820135"
				+ "83810280860185019096528085526100fa95369593946024949385019291829185019084908082843750949"
				+ "75061036c9650505050505050565b34801561021b57600080fd5b506100fa6004356103c6565b3480156102"
				+ "3357600080fd5b506101116103f7565b34801561024857600080fd5b50610111610406565b34801561025d5"
				+ "7600080fd5b506100fa600160a060020a0360043516602435610415565b34801561028157600080fd5b5061"
				+ "00fa600435602435151561044d565b34801561029e57600080fd5b506100fa610483565b600154604080518"
				+ "48152602081018490528151600160a060020a0390931692818301926000928290030181855af45050505050"
				+ "565b600654600160a060020a031681565b600354600160a060020a031681565b816080528060a0526000608"
				+ "060406080620100016000f4151561031c57600080fd5b5050565b600254600160a060020a031681565b6004"
				+ "54600160a060020a031681565b600354604051600160a060020a03909116906000818181855af4505050565"
				+ "b600554600160a060020a031681565b6005546040518251600160a060020a03909216918391908190602080"
				+ "8501910280838360005b838110156103aa578181015183820152602001610392565b5050505090500191505"
				+ "0600060405180830381855af450505050565b600654604080518381529051600160a060020a039092169160"
				+ "208083019260009291908290030181855af450505050565b600054600160a060020a031681565b600154600"
				+ "160a060020a031681565b6000805460408051600160a060020a038681168252602082018690528251931693"
				+ "81830193909290918290030181855af45050505050565b60045460408051848152831515602082015281516"
				+ "00160a060020a0390931692818301926000928290030181855af45050505050565b600254604051600160a0"
				+ "60020a03909116906000818181855af45050505600a165627a7a7230582076efe233a097282a46d3aefb879"
				+ "b720ed02a4ad3c6cf053cc5936a01e366c7dc0029";
		String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"frozen_Balance\",\"type\":\"uint256"
				+ "\"},{\"name\":\"frozen_Duration\",\"type\":\"uint256\"}],\"name\":\"freezeBalance\",\"o"
				+ "utputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}"
				+ ",{\"constant\":true,\"inputs\":[],\"name\":\"deleteProposalAddress\",\"outputs\":[{\"na"
				+ "me\":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type"
				+ "\""
				+ ":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"withdrawBalanceAddress\",\"o"
				+ "utputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\""
				+ ":\""
				+ "view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"witnessAddr\""
				+ ",\"type\":\"address\"},{\"name\":\"voteValue\",\"type\":\"uint256\"}],\"name\":\"voteUs"
				+ "ingAssembly\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"typ"
				+ "e\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"unFreezeBalanceAddress\""
				+ ",\"outputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability"
				+ "\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"approveP"
				+ "roposalAddress\",\"outputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,"
				+ "\""
				+ "stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"n"
				+ "ame\":\"withdrawBalance\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpay"
				+ "able\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"createProposa"
				+ "lAddress\",\"outputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"state"
				+ "Mutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":"
				+ "\"data\",\"type\":\"bytes32[]\"}],\"name\":\"createProposal\",\"outputs\":[],\"payable"
				+ "\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,"
				+ "\"inputs\":[{\"name\":\"id\",\"type\":\"uint256\"}],\"name\":\"deleteProposal\",\"outpu"
				+ "ts\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\""
				+ "constant\":true,\"inputs\":[],\"name\":\"voteContractAddress\",\"outputs\":[{\"name\":"
				+ "\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\""
				+ "function\"},{\"constant\":true,\"inputs\":[],\"name\":\"freezeBalanceAddress\",\"output"
				+ "s\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view"
				+ "\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"witnessAddr\",\""
				+ "type\":\"address\"},{\"name\":\"voteValue\",\"type\":\"uint256\"}],\"name\":\"voteForS"
				+ "ingleWitness\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"t"
				+ "ype\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"id\",\"type\":\"uint25"
				+ "6\"},{\"name\":\"isApprove\",\"type\":\"bool\"}],\"name\":\"approveProposal\",\"output"
				+ "s\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\""
				+ "constant\":false,\"inputs\":[],\"name\":\"unFreezeBalance\",\"outputs\":[],\"payable\""
				+ ":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";

		String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code, "",
				maxFeeLimit, 0L, 100, null, contract002Key, contract002Address, blockingStubFull);
		PublicMethed.waitProduceNextBlock(blockingStubFull1);

		logger.info(txid);
		Optional<TransactionInfo> infoById = PublicMethed
				.getTransactionInfoById(txid, blockingStubFull);
		com.google.protobuf.ByteString contractAddress = infoById.get().getContractAddress();
		SmartContract smartContract = PublicMethed
				.getContract(contractAddress.toByteArray(), blockingStubFull);
		Assert.assertTrue(smartContract.getAbi() != null);
		PublicMethed.waitProduceNextBlock(blockingStubFull1);
		PublicMethed.waitProduceNextBlock(blockingStubFull);
		accountResource = PublicMethed.getAccountResource(contract002Address, blockingStubFull1);
		energyLimit = accountResource.getEnergyLimit();
		energyUsage = accountResource.getEnergyUsed();
		Long balanceAfter = PublicMethed.queryAccount(contract002Address, blockingStubFull1)
				.getBalance();

		logger.info("after energy limit is " + Long.toString(energyLimit));
		logger.info("after energy usage is " + Long.toString(energyUsage));
		logger.info("after balance is " + Long.toString(balanceAfter));
		logger.info("transaction fee is " + Long.toString(infoById.get().getFee()));

		Assert.assertTrue(energyUsage > 0);
		Assert.assertTrue(balanceBefore == balanceAfter + infoById.get().getFee());
		PublicMethed.unFreezeBalance(contract002Address, contract002Key, 1,
				contract002Address, blockingStubFull);

	}

	@Test(enabled = true, description = "Get smart contract with invalid address")
	public void getContractWithInvalidAddress() {
		byte[] contractAddress = contract002Address;
		SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
		logger.info(smartContract.getAbi().toString());
		Assert.assertTrue(smartContract.getAbi().toString().isEmpty());
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


