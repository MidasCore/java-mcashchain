package io.midasprotocol.core.db;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.runtime.Runtime;
import io.midasprotocol.common.runtime.RuntimeImpl;
import io.midasprotocol.common.runtime.config.VMConfig;
import io.midasprotocol.common.runtime.vm.program.InternalTransaction;
import io.midasprotocol.common.runtime.vm.program.Program.*;
import io.midasprotocol.common.runtime.vm.program.ProgramResult;
import io.midasprotocol.common.runtime.vm.program.invoke.ProgramInvokeFactoryImpl;
import io.midasprotocol.common.storage.DepositImpl;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.*;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.*;
import io.midasprotocol.protos.Contract.TriggerSmartContract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;
import io.midasprotocol.protos.Protocol.Transaction.Result.ContractResult;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.StringUtils;

import java.util.Objects;

import static io.midasprotocol.common.runtime.vm.program.InternalTransaction.TrxType.*;

@Slf4j(topic = "TransactionTrace")
public class TransactionTrace {

	private TransactionCapsule trx;

	private ReceiptCapsule receipt;

	private Manager dbManager;

	private Runtime runtime;

	private EnergyProcessor energyProcessor;

	private InternalTransaction.TrxType trxType;

	private long txStartTimeInMs;
	@Getter
	@Setter
	private TimeResultType timeResultType = TimeResultType.NORMAL;

	public TransactionTrace(TransactionCapsule trx, Manager dbManager) {
		this.trx = trx;
		Transaction.Contract.ContractType contractType = this.trx.getInstance().getRawData()
			.getContract(0).getType();
		switch (contractType.getNumber()) {
			case ContractType.TriggerSmartContract_VALUE:
				trxType = TRX_CONTRACT_CALL_TYPE;
				break;
			case ContractType.CreateSmartContract_VALUE:
				trxType = TRX_CONTRACT_CREATION_TYPE;
				break;
			default:
				trxType = TRX_PRECOMPILED_TYPE;
		}

		this.dbManager = dbManager;
		this.receipt = new ReceiptCapsule(Sha256Hash.ZERO_HASH);

		this.energyProcessor = new EnergyProcessor(this.dbManager);
	}

	public TransactionCapsule getTrx() {
		return trx;
	}

	private boolean needVM() {
		return this.trxType == TRX_CONTRACT_CALL_TYPE || this.trxType == TRX_CONTRACT_CREATION_TYPE;
	}

	public void init(BlockCapsule blockCap) {
		init(blockCap, false);
	}

	//pre transaction check
	public void init(BlockCapsule blockCap, boolean eventPluginLoaded) {
		txStartTimeInMs = System.currentTimeMillis();
		DepositImpl deposit = DepositImpl.createRoot(dbManager);
		runtime = new RuntimeImpl(this, blockCap, deposit, new ProgramInvokeFactoryImpl());
		runtime.setEnableEventListener(eventPluginLoaded);
	}

	public void checkIsConstant() throws ContractValidateException, VMIllegalException {
		if (VMConfig.allowVmConstantinople()) {
			return;
		}

		TriggerSmartContract triggerContractFromTransaction = ContractCapsule
			.getTriggerContractFromTransaction(this.getTrx().getInstance());
		if (InternalTransaction.TrxType.TRX_CONTRACT_CALL_TYPE == this.trxType) {
			DepositImpl deposit = DepositImpl.createRoot(dbManager);
			ContractCapsule contract = deposit
				.getContract(triggerContractFromTransaction.getContractAddress().toByteArray());
			if (contract == null) {
				String msg = "contract: " + Wallet
					.encodeBase58Check(triggerContractFromTransaction.getContractAddress().toByteArray())
					+ " is not in contract store";
				logger.info(msg);
				throw new ContractValidateException(msg);
			}
			Protocol.SmartContract.ABI abi = contract.getInstance().getAbi();
			if (Wallet.isConstant(abi, triggerContractFromTransaction)) {
				throw new VMIllegalException("cannot call constant method");
			}
		}
	}

	//set bill
	public void setBill(long energyUsage) {
		if (energyUsage < 0) {
			energyUsage = 0L;
		}
		receipt.setEnergyUsageTotal(energyUsage);
	}

	//set net bill
	public void setNetBill(long netUsage, long netFee) {
		receipt.setBandwidthUsage(netUsage);
		receipt.setBandwidthFee(netFee);
	}

	public void addNetBill(long netFee) {
		receipt.addBandwidthFee(netFee);
	}

	public void exec()
		throws ContractExeException, ContractValidateException, VMIllegalException {
		/*  VM execute  */
		runtime.execute();
		runtime.go();

		if (TRX_PRECOMPILED_TYPE != runtime.getTrxType()) {
			if (ContractResult.OUT_OF_TIME
				.equals(receipt.getResult())) {
				setTimeResultType(TimeResultType.OUT_OF_TIME);
			} else if (System.currentTimeMillis() - txStartTimeInMs
				> Args.getInstance().getLongRunningTime()) {
				setTimeResultType(TimeResultType.LONG_RUNNING);
			}
		}
	}

	public void finalization() throws ContractExeException {
		try {
			pay();
		} catch (BalanceInsufficientException e) {
			throw new ContractExeException(e.getMessage());
		}
		runtime.finalization();
	}

	/**
	 * pay actually bill(include ENERGY and storage).
	 */
	public void pay() throws BalanceInsufficientException {
		byte[] originAccount;
		byte[] callerAccount;
		long percent = 0;
		long originEnergyLimit = 0;
		switch (trxType) {
			case TRX_CONTRACT_CREATION_TYPE:
				callerAccount = TransactionCapsule.getOwner(trx.getInstance().getRawData().getContract(0));
				originAccount = callerAccount;
				break;
			case TRX_CONTRACT_CALL_TYPE:
				// todo: check
				TriggerSmartContract callContract = ContractCapsule
					.getTriggerContractFromTransaction(trx.getInstance());
				if (callContract != null && callContract.getData().startsWith(ByteString.copyFrom(ByteArray.fromHexString("a9059cbb")))) {
					receipt.setEnergyUsageTotal(0);
				}
				ContractCapsule contractCapsule =
					dbManager.getContractStore().get(callContract.getContractAddress().toByteArray());

				callerAccount = callContract.getOwnerAddress().toByteArray();
				originAccount = contractCapsule.getOriginAddress();
				percent = Math.max(Constant.ONE_HUNDRED - contractCapsule.getConsumeUserResourcePercent(), 0);
				percent = Math.min(percent, Constant.ONE_HUNDRED);
				originEnergyLimit = contractCapsule.getOriginEnergyLimit();
				break;
			default:
				return;
		}

		// originAccount Percent = 30%
		AccountCapsule origin = dbManager.getAccountStore().get(originAccount);
		AccountCapsule caller = dbManager.getAccountStore().get(callerAccount);
		receipt.payEnergyBill(
			dbManager,
			origin,
			caller,
			percent, originEnergyLimit,
			energyProcessor,
			dbManager.getWitnessController().getHeadSlot());
	}

	public boolean checkNeedRetry() {
		if (!needVM()) {
			return false;
		}
		return trx.getContractRet() != ContractResult.OUT_OF_TIME && receipt.getResult()
			== ContractResult.OUT_OF_TIME;
	}

	public void check() throws ReceiptCheckErrException {
		if (!needVM()) {
			return;
		}
		if (Objects.isNull(trx.getContractRet())) {
			throw new ReceiptCheckErrException("null resultCode");
		}
		if (!trx.getContractRet().equals(receipt.getResult())) {
			logger.info(
				"this tx id: {}, the resultCode in received block: {}, the resultCode in self: {}",
				Hex.toHexString(trx.getTransactionId().getBytes()), trx.getContractRet(),
				receipt.getResult());
			throw new ReceiptCheckErrException("Different resultCode");
		}
	}

	public ReceiptCapsule getReceipt() {
		return receipt;
	}

	public void setResult() {
		if (!needVM()) {
			return;
		}
		RuntimeException exception = runtime.getResult().getException();
		if (Objects.isNull(exception) && StringUtils
			.isEmpty(runtime.getRuntimeError()) && !runtime.getResult().isRevert()) {
			receipt.setResult(ContractResult.OK);
			return;
		}
		if (runtime.getResult().isRevert()) {
			receipt.setResult(ContractResult.REVERT);
			return;
		}
		if (exception instanceof IllegalOperationException) {
			receipt.setResult(ContractResult.ILLEGAL_OPERATION);
			return;
		}
		if (exception instanceof OutOfEnergyException) {
			receipt.setResult(ContractResult.OUT_OF_ENERGY);
			return;
		}
		if (exception instanceof BadJumpDestinationException) {
			receipt.setResult(ContractResult.BAD_JUMP_DESTINATION);
			return;
		}
		if (exception instanceof OutOfTimeException) {
			receipt.setResult(ContractResult.OUT_OF_TIME);
			return;
		}
		if (exception instanceof OutOfMemoryException) {
			receipt.setResult(ContractResult.OUT_OF_MEMORY);
			return;
		}
		if (exception instanceof PrecompiledContractException) {
			receipt.setResult(ContractResult.PRECOMPILED_CONTRACT);
			return;
		}
		if (exception instanceof StackTooSmallException) {
			receipt.setResult(ContractResult.STACK_TOO_SMALL);
			return;
		}
		if (exception instanceof StackTooLargeException) {
			receipt.setResult(ContractResult.STACK_TOO_LARGE);
			return;
		}
		if (exception instanceof JVMStackOverFlowException) {
			receipt.setResult(ContractResult.JVM_STACK_OVER_FLOW);
			return;
		}
		if (exception instanceof TransferException) {
			receipt.setResult(ContractResult.TRANSFER_FAILED);
			return;
		}
		receipt.setResult(ContractResult.UNKNOWN);
	}

	public String getRuntimeError() {
		return runtime.getRuntimeError();
	}

	public ProgramResult getRuntimeResult() {
		return runtime.getResult();
	}

	public Runtime getRuntime() {
		return runtime;
	}

	public enum TimeResultType {
		NORMAL,
		LONG_RUNNING,
		OUT_OF_TIME
	}
}
