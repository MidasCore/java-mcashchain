package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.runtime.vm.LogInfo;
import io.midasprotocol.common.runtime.vm.program.InternalTransaction;
import io.midasprotocol.common.runtime.vm.program.ProgramResult;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.TransactionTrace;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.TransactionInfo;
import io.midasprotocol.protos.Protocol.TransactionInfo.Log;
import io.midasprotocol.protos.Protocol.TransactionInfo.code;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j(topic = "capsule")
public class TransactionInfoCapsule implements ProtoCapsule<TransactionInfo> {

	private TransactionInfo transactionInfo;

	/**
	 * constructor TransactionCapsule.
	 */
	public TransactionInfoCapsule(TransactionInfo trxRet) {
		this.transactionInfo = trxRet;
	}

	public TransactionInfoCapsule(byte[] data) throws BadItemException {
		try {
			this.transactionInfo = TransactionInfo.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			throw new BadItemException("TransactionInfoCapsule proto data parse exception");
		}
	}

	public TransactionInfoCapsule() {
		this.transactionInfo = TransactionInfo.newBuilder().build();
	}

	public static TransactionInfoCapsule buildInstance(TransactionCapsule trxCap, BlockCapsule block,
													   TransactionTrace trace) {

		TransactionInfo.Builder builder = TransactionInfo.newBuilder();
		ReceiptCapsule traceReceipt = trace.getReceipt();
		builder.setResult(code.SUCCESS);
		if (StringUtils.isNoneEmpty(trace.getRuntimeError()) || Objects
			.nonNull(trace.getRuntimeResult().getException())) {
			builder.setResult(code.FAILED);
			builder.setResMessage(ByteString.copyFromUtf8(trace.getRuntimeError()));
		}
		builder.setId(ByteString.copyFrom(trxCap.getTransactionId().getBytes()));
		ProgramResult programResult = trace.getRuntimeResult();
		long fee =
			programResult.getRet().getFee() + traceReceipt.getEnergyFee()
				+ traceReceipt.getNetFee() + traceReceipt.getMultiSignFee();
		ByteString contractResult = ByteString.copyFrom(programResult.getHReturn());
		ByteString ContractAddress = ByteString.copyFrom(programResult.getContractAddress());

		builder.setFee(fee);
		builder.addContractResult(contractResult);
		builder.setContractAddress(ContractAddress);
		builder.setUnfreezeAmount(programResult.getRet().getUnfreezeAmount());
		builder.setAssetIssueID(programResult.getRet().getAssetIssueID());
		builder.setExchangeId(programResult.getRet().getExchangeId());
		builder.setWithdrawAmount(programResult.getRet().getWithdrawAmount());
		builder.setUnstakeAmount(programResult.getRet().getUnstakeAmount());
		builder.setExchangeReceivedAmount(programResult.getRet().getExchangeReceivedAmount());
		builder.setExchangeInjectAnotherAmount(programResult.getRet().getExchangeInjectAnotherAmount());
		builder.setExchangeWithdrawAnotherAmount(
			programResult.getRet().getExchangeWithdrawAnotherAmount());

		List<Log> logList = new ArrayList<>();
		programResult.getLogInfoList().forEach(
			logInfo -> {
				logList.add(LogInfo.buildLog(logInfo));
			}
		);
		builder.addAllLog(logList);

		if (Objects.nonNull(block)) {
			builder.setBlockNumber(block.getInstance().getBlockHeader().getRawData().getNumber());
			builder.setBlockTimeStamp(block.getInstance().getBlockHeader().getRawData().getTimestamp());
		}

		builder.setReceipt(traceReceipt.getReceipt());

		if (Args.getInstance().isSaveInternalTx() && null != programResult.getInternalTransactions()) {
			for (InternalTransaction internalTransaction : programResult.getInternalTransactions()) {
				Protocol.InternalTransaction.Builder internalTrxBuilder = Protocol.InternalTransaction
					.newBuilder();
				// set hash
				internalTrxBuilder.setHash(ByteString.copyFrom(internalTransaction.getHash()));
				// set caller
				internalTrxBuilder.setCallerAddress(ByteString.copyFrom(internalTransaction.getSender()));
				// set TransferTo
				internalTrxBuilder
					.setTransferToAddress(ByteString.copyFrom(internalTransaction.getTransferToAddress()));
				//TODO: "for loop" below in future for multiple token case, we only have one for now.
				Protocol.InternalTransaction.CallValueInfo.Builder callValueInfoBuilder =
					Protocol.InternalTransaction.CallValueInfo.newBuilder();
				// trx will not be set token name
				callValueInfoBuilder.setCallValue(internalTransaction.getValue());
				// Just one transferBuilder for now.
				internalTrxBuilder.addCallValueInfo(callValueInfoBuilder);
				internalTransaction.getTokenInfo().forEach((tokenId, amount) -> {
					Protocol.InternalTransaction.CallValueInfo.Builder tokenInfoBuilder =
						Protocol.InternalTransaction.CallValueInfo.newBuilder();
					tokenInfoBuilder.setTokenId(tokenId);
					tokenInfoBuilder.setCallValue(amount);
					internalTrxBuilder.addCallValueInfo(tokenInfoBuilder);
				});
				// Token for loop end here
				internalTrxBuilder.setNote(ByteString.copyFrom(internalTransaction.getNote().getBytes()));
				internalTrxBuilder.setRejected(internalTransaction.isRejected());
				builder.addInternalTransactions(internalTrxBuilder);
			}
		}

		return new TransactionInfoCapsule(builder.build());
	}

	public long getFee() {
		return transactionInfo.getFee();
	}

	public void setFee(long fee) {
		this.transactionInfo = this.transactionInfo.toBuilder().setFee(fee).build();
	}

	public byte[] getId() {
		return transactionInfo.getId().toByteArray();
	}

	public void setId(byte[] id) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.setId(ByteString.copyFrom(id)).build();
	}

	public long getUnfreezeAmount() {
		return transactionInfo.getUnfreezeAmount();
	}

	public void setUnfreezeAmount(long amount) {
		this.transactionInfo = this.transactionInfo.toBuilder().setUnfreezeAmount(amount).build();
	}

	public long getWithdrawAmount() {
		return transactionInfo.getWithdrawAmount();
	}

	public void setWithdrawAmount(long amount) {
		this.transactionInfo = this.transactionInfo.toBuilder().setWithdrawAmount(amount).build();
	}

	public long getUnstakeAmount() {
		return transactionInfo.getUnstakeAmount();
	}

	public void setUnstakeAmount(long amount) {
		this.transactionInfo = this.transactionInfo.toBuilder().setUnstakeAmount(amount).build();
	}

	public void setResult(code result) {
		this.transactionInfo = this.transactionInfo.toBuilder().setResult(result).build();
	}

	public void setResMessage(String message) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.setResMessage(ByteString.copyFromUtf8(message)).build();
	}

	public void addFee(long fee) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.setFee(this.transactionInfo.getFee() + fee).build();
	}

	public long getBlockNumber() {
		return transactionInfo.getBlockNumber();
	}

	public void setBlockNumber(long num) {
		this.transactionInfo = this.transactionInfo.toBuilder().setBlockNumber(num)
			.build();
	}

	public long getBlockTimeStamp() {
		return transactionInfo.getBlockTimeStamp();
	}

	public void setBlockTimeStamp(long time) {
		this.transactionInfo = this.transactionInfo.toBuilder().setBlockTimeStamp(time)
			.build();
	}

	public void setContractResult(byte[] ret) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.addContractResult(ByteString.copyFrom(ret))
			.build();
	}

	public void setContractAddress(byte[] contractAddress) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.setContractAddress(ByteString.copyFrom(contractAddress))
			.build();
	}

	public void setReceipt(ReceiptCapsule receipt) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.setReceipt(receipt.getReceipt())
			.build();
	}

	public void addAllLog(List<Log> logs) {
		this.transactionInfo = this.transactionInfo.toBuilder()
			.addAllLog(logs)
			.build();
	}

	@Override
	public byte[] getData() {
		return this.transactionInfo.toByteArray();
	}

	@Override
	public TransactionInfo getInstance() {
		return this.transactionInfo;
	}
}