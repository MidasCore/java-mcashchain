package io.midasprotocol.common.overlay.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.midasprotocol.api.GrpcAPI.*;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.Transaction;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class WalletGrpcClient {

	private final ManagedChannel channel;
	private final WalletGrpc.WalletBlockingStub walletBlockingStub;

	public WalletGrpcClient(String host, int port) {
		channel = ManagedChannelBuilder.forAddress(host, port)
			.usePlaintext(true)
			.build();
		walletBlockingStub = WalletGrpc.newBlockingStub(channel);
	}

	public WalletGrpcClient(String host) {
		channel = ManagedChannelBuilder.forTarget(host)
			.usePlaintext(true)
			.build();
		walletBlockingStub = WalletGrpc.newBlockingStub(channel);
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	public Account queryAccount(byte[] address) {
		ByteString addressByteString = ByteString.copyFrom(address);
		Account request = Account.newBuilder().setAddress(addressByteString).build();
		return walletBlockingStub.getAccount(request);
	}

	public TransactionExtension createTransaction(Contract.TransferContract contract) {
		return walletBlockingStub.createTransaction(contract);
	}

	public TransactionExtension createTransferAssetTransaction(Contract.TransferAssetContract contract) {
		return walletBlockingStub.transferAsset(contract);
	}

	public TransactionExtension createParticipateAssetIssueTransaction(
		Contract.ParticipateAssetIssueContract contract) {
		return walletBlockingStub.participateAssetIssue(contract);
	}

	public TransactionExtension createAssetIssue(AssetIssueContract contract) {
		return walletBlockingStub.createAssetIssue(contract);
	}

	public TransactionExtension voteWitnessAccount(Contract.VoteWitnessContract contract) {
		return walletBlockingStub.voteWitnessAccount(contract);
	}

	public TransactionExtension createWitness(Contract.WitnessCreateContract contract) {
		return walletBlockingStub.createWitness(contract);
	}

	public boolean broadcastTransaction(Transaction signaturedTransaction) {
		Return response = walletBlockingStub.broadcastTransaction(signaturedTransaction);
		return response.getResult();
	}

	public BlockExtension getBlock(long blockNum) {
		if (blockNum < 0) {
			return walletBlockingStub.getNowBlock(EmptyMessage.newBuilder().build());
		}
		NumberMessage.Builder builder = NumberMessage.newBuilder();
		builder.setNum(blockNum);
		return walletBlockingStub.getBlockByNum(builder.build());
	}

	public Optional<NodeList> listNodes() {
		NodeList nodeList = walletBlockingStub
			.listNodes(EmptyMessage.newBuilder().build());
		if (nodeList != null) {
			return Optional.of(nodeList);
		}
		return Optional.empty();
	}

	public Optional<AssetIssueList> getAssetIssueByAccount(byte[] address) {
		ByteString addressByteString = ByteString.copyFrom(address);
		Account request = Account.newBuilder().setAddress(addressByteString).build();
		AssetIssueList assetIssueList = walletBlockingStub
			.getAssetIssueByAccount(request);
		if (assetIssueList != null) {
			return Optional.of(assetIssueList);
		}
		return Optional.empty();
	}

	public AssetIssueContract getAssetIssueById(String assetId) {
		ByteString assetIdBs = ByteString.copyFrom(assetId.getBytes());
		BytesMessage request = BytesMessage.newBuilder().setValue(assetIdBs).build();
		return walletBlockingStub.getAssetIssueById(request);
	}

}
