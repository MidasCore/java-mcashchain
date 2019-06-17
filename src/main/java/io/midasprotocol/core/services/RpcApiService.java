package io.midasprotocol.core.services;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.midasprotocol.api.DatabaseGrpc.DatabaseImplBase;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.*;
import io.midasprotocol.api.GrpcAPI.Return.ResponseCode;
import io.midasprotocol.api.WalletExtensionGrpc;
import io.midasprotocol.api.WalletGrpc.WalletImplBase;
import io.midasprotocol.api.WalletSolidityGrpc.WalletSolidityImplBase;
import io.midasprotocol.common.application.Service;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.common.overlay.discover.node.NodeHandler;
import io.midasprotocol.common.overlay.discover.node.NodeManager;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.common.utils.Utils;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.WalletSolidity;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.NonUniqueObjectException;
import io.midasprotocol.core.exception.StoreException;
import io.midasprotocol.core.exception.VMIllegalException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.*;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.*;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j(topic = "API")
public class RpcApiService implements Service {

	private static final long BLOCK_LIMIT_NUM = 100;
	private static final long TRANSACTION_LIMIT_NUM = 1000;
	private int port = Args.getInstance().getRpcPort();
	private Server apiServer;
	@Autowired
	private Manager dbManager;
	@Autowired
	private NodeManager nodeManager;
	@Autowired
	private WalletSolidity walletSolidity;
	@Autowired
	private Wallet wallet;
	@Autowired
	private NodeInfoService nodeInfoService;
	@Getter
	private DatabaseApi databaseApi = new DatabaseApi();
	private WalletApi walletApi = new WalletApi();
	@Getter
	private WalletSolidityApi walletSolidityApi = new WalletSolidityApi();

	@Override
	public void init() {
	}

	@Override
	public void init(Args args) {
	}

	@Override
	public void start() {
		try {
			NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port)
				.addService(databaseApi);

			Args args = Args.getInstance();

			if (args.getRpcThreadNum() > 0) {
				serverBuilder = serverBuilder
					.executor(Executors.newFixedThreadPool(args.getRpcThreadNum()));
			}

			if (args.isSolidityNode()) {
				serverBuilder = serverBuilder.addService(walletSolidityApi);
				if (args.isWalletExtensionApi()) {
					serverBuilder = serverBuilder.addService(new WalletExtensionApi());
				}
			} else {
				serverBuilder = serverBuilder.addService(walletApi);
			}

			// Set configs from config.conf or default value
			serverBuilder
				.maxConcurrentCallsPerConnection(args.getMaxConcurrentCallsPerConnection())
				.flowControlWindow(args.getFlowControlWindow())
				.maxConnectionIdle(args.getMaxConnectionIdleInMillis(), TimeUnit.MILLISECONDS)
				.maxConnectionAge(args.getMaxConnectionAgeInMillis(), TimeUnit.MILLISECONDS)
				.maxMessageSize(args.getMaxMessageSize())
				.maxHeaderListSize(args.getMaxHeaderListSize());

			apiServer = serverBuilder.build().start();
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		}

		logger.info("RpcApiService started, listening on " + port);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.err.println("*** shutting down gRPC server since JVM is shutting down");
			//server.this.stop();
			System.err.println("*** server shut down");
		}));
	}

	private TransactionExtension transaction2Extension(Transaction transaction) {
		if (transaction == null) {
			return null;
		}
		TransactionExtension.Builder trxExtBuilder = TransactionExtension.newBuilder();
		Return.Builder retBuilder = Return.newBuilder();
		trxExtBuilder.setTransaction(transaction);
		trxExtBuilder.setTxId(Sha256Hash.of(transaction.getRawData().toByteArray()).getByteString());
		retBuilder.setResult(true).setCode(ResponseCode.SUCCESS);
		trxExtBuilder.setResult(retBuilder);
		return trxExtBuilder.build();
	}

	private BlockExtension block2Extension(Block block) {
		if (block == null) {
			return null;
		}
		BlockExtension.Builder builder = BlockExtension.newBuilder();
		BlockCapsule blockCapsule = new BlockCapsule(block);
		builder.setBlockHeader(block.getBlockHeader());
		builder.setBlockId(ByteString.copyFrom(blockCapsule.getBlockId().getBytes()));
		for (int i = 0; i < block.getTransactionsCount(); i++) {
			Transaction transaction = block.getTransactions(i);
			builder.addTransactions(transaction2Extension(transaction));
		}
		return builder.build();
	}

	@Override
	public void stop() {
		if (apiServer != null) {
			apiServer.shutdown();
		}
	}

	/**
	 * ...
	 */
	public void blockUntilShutdown() {
		if (apiServer != null) {
			try {
				apiServer.awaitTermination();
			} catch (InterruptedException e) {
				logger.debug(e.getMessage(), e);
			}
		}
	}

	/**
	 * DatabaseApi.
	 */
	public class DatabaseApi extends DatabaseImplBase {

		@Override
		public void getBlockReference(io.midasprotocol.api.GrpcAPI.EmptyMessage request,
									  io.grpc.stub.StreamObserver<io.midasprotocol.api.GrpcAPI.BlockReference> responseObserver) {
			long headBlockNum = dbManager.getDynamicPropertiesStore()
				.getLatestBlockHeaderNumber();
			byte[] blockHeaderHash = dbManager.getDynamicPropertiesStore()
				.getLatestBlockHeaderHash().getBytes();
			BlockReference ref = BlockReference.newBuilder()
				.setBlockHash(ByteString.copyFrom(blockHeaderHash))
				.setBlockNum(headBlockNum)
				.build();
			responseObserver.onNext(ref);
			responseObserver.onCompleted();
		}

		@Override
		public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
			Block block = null;
			try {
				block = dbManager.getHead().getInstance();
			} catch (StoreException e) {
				logger.error(e.getMessage());
			}
			responseObserver.onNext(block);
			responseObserver.onCompleted();
		}

		@Override
		public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
			Block block = null;
			try {
				block = dbManager.getBlockByNum(request.getNum()).getInstance();
			} catch (StoreException e) {
				logger.error(e.getMessage());
			}
			responseObserver.onNext(block);
			responseObserver.onCompleted();
		}

		@Override
		public void getDynamicProperties(EmptyMessage request,
										 StreamObserver<DynamicProperties> responseObserver) {
			DynamicProperties.Builder builder = DynamicProperties.newBuilder();
			builder.setLastSolidityBlockNum(
				dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
			DynamicProperties dynamicProperties = builder.build();
			responseObserver.onNext(dynamicProperties);
			responseObserver.onCompleted();
		}
	}

	/**
	 * WalletSolidityApi.
	 */
	public class WalletSolidityApi extends WalletSolidityImplBase {

		@Override
		public void getAccount(Account request, StreamObserver<Account> responseObserver) {
			ByteString addressBs = request.getAddress();
			if (addressBs != null) {
				Account reply = wallet.getAccount(request);
				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAccountById(Account request, StreamObserver<Account> responseObserver) {
			ByteString id = request.getAccountId();
			if (id != null) {
				Account reply = wallet.getAccountById(request);
				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
			responseObserver.onNext(wallet.getWitnessList());
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueList(EmptyMessage request,
									  StreamObserver<AssetIssueList> responseObserver) {
			responseObserver.onNext(wallet.getAssetIssueList());
			responseObserver.onCompleted();
		}

		@Override
		public void getPaginatedAssetIssueList(PaginatedMessage request,
											   StreamObserver<AssetIssueList> responseObserver) {
			responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueByName(BytesMessage request,
										StreamObserver<AssetIssueContract> responseObserver) {
			ByteString assetName = request.getValue();
			if (assetName != null) {
				try {
					responseObserver.onNext(wallet.getAssetIssueByName(assetName));
				} catch (NonUniqueObjectException e) {
					responseObserver.onNext(null);
					logger.error("Solidity NonUniqueObjectException: {}", e.getMessage());
				}
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueListByName(BytesMessage request,
											StreamObserver<AssetIssueList> responseObserver) {
			ByteString assetName = request.getValue();

			if (assetName != null) {
				responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueById(BytesMessage request,
									  StreamObserver<AssetIssueContract> responseObserver) {
			ByteString assetId = request.getValue();

			if (assetId != null) {
				responseObserver.onNext(wallet.getAssetIssueById(Longs.fromByteArray(assetId.toByteArray())));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getNowBlock(EmptyMessage request,
								StreamObserver<BlockExtension> responseObserver) {
			responseObserver.onNext(block2Extension(wallet.getNowBlock()));
			responseObserver.onCompleted();
		}

		@Override
		public void getBlockByNum(NumberMessage request,
								  StreamObserver<BlockExtension> responseObserver) {
			long num = request.getNum();
			if (num >= 0) {
				Block reply = wallet.getBlockByNum(num);
				responseObserver.onNext(block2Extension(reply));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}


		@Override
		public void getDelegatedResource(DelegatedResourceMessage request,
										 StreamObserver<DelegatedResourceList> responseObserver) {
			responseObserver
				.onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
			responseObserver.onCompleted();
		}

		@Override
		public void getDelegatedResourceAccountIndex(BytesMessage request,
													 StreamObserver<io.midasprotocol.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
			responseObserver
				.onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
			responseObserver.onCompleted();
		}

		@Override
		public void getExchangeById(BytesMessage request,
									StreamObserver<Exchange> responseObserver) {
			ByteString exchangeId = request.getValue();

			if (Objects.nonNull(exchangeId)) {
				responseObserver.onNext(wallet.getExchangeById(exchangeId));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void listExchanges(EmptyMessage request,
								  StreamObserver<ExchangeList> responseObserver) {
			responseObserver.onNext(wallet.getExchangeList());
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionCountByBlockNum(NumberMessage request,
												  StreamObserver<NumberMessage> responseObserver) {
			NumberMessage.Builder builder = NumberMessage.newBuilder();
			try {
				Block block = dbManager.getBlockByNum(request.getNum()).getInstance();
				builder.setNum(block.getTransactionsCount());
			} catch (StoreException e) {
				logger.error(e.getMessage());
				builder.setNum(-1);
			}
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionById(BytesMessage request,
									   StreamObserver<Transaction> responseObserver) {
			ByteString id = request.getValue();
			if (null != id) {
				Transaction reply = wallet.getTransactionById(id);

				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionInfoById(BytesMessage request,
										   StreamObserver<TransactionInfo> responseObserver) {
			ByteString id = request.getValue();
			if (null != id) {
				TransactionInfo reply = wallet.getTransactionInfoById(id);

				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void generateAddress(EmptyMessage request,
									StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
			ECKey ecKey = new ECKey(Utils.getRandom());
			byte[] priKey = ecKey.getPrivKeyBytes();
			byte[] address = ecKey.getAddress();
			String addressStr = Wallet.encodeBase58Check(address);
			String priKeyStr = Hex.encodeHexString(priKey);
			AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
			builder.setAddress(addressStr);
			builder.setPrivateKey(priKeyStr);
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		}
	}

	/**
	 * WalletExtensionApi.
	 */
	public class WalletExtensionApi extends WalletExtensionGrpc.WalletExtensionImplBase {

		private TransactionListExtension transactionList2Extention(TransactionList transactionList) {
			if (transactionList == null) {
				return null;
			}
			TransactionListExtension.Builder builder = TransactionListExtension.newBuilder();
			for (Transaction transaction : transactionList.getTransactionList()) {
				builder.addTransactions(transaction2Extension(transaction));
			}
			return builder.build();
		}

		@Override
		public void getTransactionsFromThis(AccountPaginated request,
											StreamObserver<TransactionListExtension> responseObserver) {
			ByteString thisAddress = request.getAccount().getAddress();
			long offset = request.getOffset();
			long limit = request.getLimit();
			if (null != thisAddress && offset >= 0 && limit >= 0) {
				TransactionList reply = walletSolidity
					.getTransactionsFromThis(thisAddress, offset, limit);
				responseObserver.onNext(transactionList2Extention(reply));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionsToThis(AccountPaginated request,
										  StreamObserver<TransactionListExtension> responseObserver) {
			ByteString toAddress = request.getAccount().getAddress();
			long offset = request.getOffset();
			long limit = request.getLimit();
			if (null != toAddress && offset >= 0 && limit >= 0) {
				TransactionList reply = walletSolidity
					.getTransactionsToThis(toAddress, offset, limit);
				responseObserver.onNext(transactionList2Extention(reply));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}
	}

	/**
	 * WalletApi.
	 */
	public class WalletApi extends WalletImplBase {

		private BlockListExtension blocklist2Extention(BlockList blockList) {
			if (blockList == null) {
				return null;
			}
			BlockListExtension.Builder builder = BlockListExtension.newBuilder();
			for (Block block : blockList.getBlockList()) {
				builder.addBlocks(block2Extension(block));
			}
			return builder.build();
		}

		@Override
		public void getAccount(Account req, StreamObserver<Account> responseObserver) {
			ByteString addressBs = req.getAddress();
			if (addressBs != null) {
				Account reply = wallet.getAccount(req);
				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAccountById(Account req, StreamObserver<Account> responseObserver) {
			ByteString accountId = req.getAccountId();
			if (accountId != null) {
				Account reply = wallet.getAccountById(req);
				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void createTransaction(TransferContract request,
									  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.TransferContract, responseObserver);
		}

		private void createTransactionExtension(Message request, ContractType contractType,
												StreamObserver<TransactionExtension> responseObserver) {
			TransactionExtension.Builder trxExtBuilder = TransactionExtension.newBuilder();
			Return.Builder retBuilder = Return.newBuilder();
			try {
				TransactionCapsule trx = createTransactionCapsule(request, contractType);
				trxExtBuilder.setTransaction(trx.getInstance());
				trxExtBuilder.setTxId(trx.getTransactionId().getByteString());
				retBuilder.setResult(true).setCode(ResponseCode.SUCCESS);
			} catch (ContractValidateException e) {
				retBuilder.setResult(false).setCode(ResponseCode.CONTRACT_VALIDATE_ERROR)
					.setMessage(ByteString.copyFromUtf8("contract validate error : " + e.getMessage()));
				logger.debug("ContractValidateException: {}", e.getMessage());
			} catch (Exception e) {
				retBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				logger.info("exception caught" + e.getMessage());
			}
			trxExtBuilder.setResult(retBuilder);
			responseObserver.onNext(trxExtBuilder.build());
			responseObserver.onCompleted();
		}

		private TransactionCapsule createTransactionCapsule(com.google.protobuf.Message message,
															ContractType contractType) throws ContractValidateException {
			return wallet.createTransactionCapsule(message, contractType);
		}

		@Override
		public void getTransactionSign(TransactionSign req,
									   StreamObserver<TransactionExtension> responseObserver) {
			TransactionExtension.Builder trxExtBuilder = TransactionExtension.newBuilder();
			Return.Builder retBuilder = Return.newBuilder();
			try {
				TransactionCapsule trx = wallet.getTransactionSign(req);
				trxExtBuilder.setTransaction(trx.getInstance());
				trxExtBuilder.setTxId(trx.getTransactionId().getByteString());
				retBuilder.setResult(true).setCode(ResponseCode.SUCCESS);
			} catch (Exception e) {
				retBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				logger.info("exception caught" + e.getMessage());
			}
			trxExtBuilder.setResult(retBuilder);
			responseObserver.onNext(trxExtBuilder.build());
			responseObserver.onCompleted();
		}

		@Override
		public void addSign(TransactionSign req,
							StreamObserver<TransactionExtension> responseObserver) {
			TransactionExtension.Builder trxExtBuilder = TransactionExtension.newBuilder();
			Return.Builder retBuilder = Return.newBuilder();
			try {
				TransactionCapsule trx = wallet.addSign(req);
				trxExtBuilder.setTransaction(trx.getInstance());
				trxExtBuilder.setTxId(trx.getTransactionId().getByteString());
				retBuilder.setResult(true).setCode(ResponseCode.SUCCESS);
			} catch (Exception e) {
				retBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				logger.info("exception caught" + e.getMessage());
			}
			trxExtBuilder.setResult(retBuilder);
			responseObserver.onNext(trxExtBuilder.build());
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionSignWeight(Transaction req,
											 StreamObserver<TransactionSignWeight> responseObserver) {
			TransactionSignWeight tsw = wallet.getTransactionSignWeight(req);
			responseObserver.onNext(tsw);
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionApprovedList(Transaction req,
											   StreamObserver<TransactionApprovedList> responseObserver) {
			TransactionApprovedList tal = wallet.getTransactionApprovedList(req);
			responseObserver.onNext(tal);
			responseObserver.onCompleted();
		}

		@Override
		public void createAddress(BytesMessage req,
								  StreamObserver<BytesMessage> responseObserver) {
			byte[] address = wallet.createAddress(req.getValue().toByteArray());
			BytesMessage.Builder builder = BytesMessage.newBuilder();
			builder.setValue(ByteString.copyFrom(address));
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		}

		private EasyTransferResponse easyTransfer(byte[] privateKey, ByteString toAddress,
												  long amount) {
			TransactionCapsule transactionCapsule;
			GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
			EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
			try {
				ECKey ecKey = ECKey.fromPrivate(privateKey);
				byte[] owner = ecKey.getAddress();
				TransferContract.Builder builder = TransferContract.newBuilder();
				builder.setOwnerAddress(ByteString.copyFrom(owner));
				builder.setToAddress(toAddress);
				builder.setAmount(amount);
				transactionCapsule = createTransactionCapsule(builder.build(),
					ContractType.TransferContract);
				transactionCapsule.sign(privateKey);
				GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
				responseBuild.setTransaction(transactionCapsule.getInstance());
				responseBuild.setTxId(transactionCapsule.getTransactionId().getByteString());
				responseBuild.setResult(retur);
			} catch (ContractValidateException e) {
				returnBuilder.setResult(false).setCode(ResponseCode.CONTRACT_VALIDATE_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getMessage()));
				responseBuild.setResult(returnBuilder.build());
			} catch (Exception e) {
				returnBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				responseBuild.setResult(returnBuilder.build());
			}

			return responseBuild.build();
		}

		private EasyTransferResponse easyTransferAsset(byte[] privateKey, ByteString toAddress,
													   long assetId, long amount) {
			TransactionCapsule transactionCapsule;
			GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
			EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
			try {
				ECKey ecKey = ECKey.fromPrivate(privateKey);
				byte[] owner = ecKey.getAddress();
				TransferAssetContract.Builder builder = TransferAssetContract.newBuilder();
				builder.setOwnerAddress(ByteString.copyFrom(owner));
				builder.setToAddress(toAddress);
				builder.setAssetId(assetId);
				builder.setAmount(amount);
				transactionCapsule = createTransactionCapsule(builder.build(),
					ContractType.TransferAssetContract);
				transactionCapsule.sign(privateKey);
				GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
				responseBuild.setTransaction(transactionCapsule.getInstance());
				responseBuild.setTxId(transactionCapsule.getTransactionId().getByteString());
				responseBuild.setResult(retur);
			} catch (ContractValidateException e) {
				returnBuilder.setResult(false).setCode(ResponseCode.CONTRACT_VALIDATE_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getMessage()));
				responseBuild.setResult(returnBuilder.build());
			} catch (Exception e) {
				returnBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				responseBuild.setResult(returnBuilder.build());
			}

			return responseBuild.build();
		}

		@Override
		public void easyTransfer(EasyTransferMessage req,
								 StreamObserver<EasyTransferResponse> responseObserver) {
			byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
			EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}

		@Override
		public void easyTransferAsset(EasyTransferAssetMessage req,
									  StreamObserver<EasyTransferResponse> responseObserver) {
			byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
			EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(),
				req.getAssetId(), req.getAmount());
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}

		@Override
		public void easyTransferByPrivate(EasyTransferByPrivateMessage req,
										  StreamObserver<EasyTransferResponse> responseObserver) {
			byte[] privateKey = req.getPrivateKey().toByteArray();
			EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}

		@Override
		public void easyTransferAssetByPrivate(EasyTransferAssetByPrivateMessage req,
											   StreamObserver<EasyTransferResponse> responseObserver) {
			byte[] privateKey = req.getPrivateKey().toByteArray();
			EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(),
				req.getAssetId(), req.getAmount());
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}

		@Override
		public void broadcastTransaction(Transaction req,
										 StreamObserver<GrpcAPI.Return> responseObserver) {
			GrpcAPI.Return retur = wallet.broadcastTransaction(req);
			responseObserver.onNext(retur);
			responseObserver.onCompleted();
		}

		@Override
		public void createAssetIssue(AssetIssueContract request,
									 StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.AssetIssueContract, responseObserver);
		}

		@Override
		public void unfreezeAsset(UnfreezeAssetContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.UnfreezeAssetContract, responseObserver);
		}

		@Override
		public void voteWitnessAccount(VoteWitnessContract request,
									   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.VoteWitnessContract, responseObserver);
		}

		@Override
		public void updateSetting(UpdateSettingContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.UpdateSettingContract,
				responseObserver);
		}

		@Override
		public void updateEnergyLimit(UpdateEnergyLimitContract request,
									  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.UpdateEnergyLimitContract,
				responseObserver);
		}

		@Override
		public void createWitness(WitnessCreateContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.WitnessCreateContract, responseObserver);
		}

		@Override
		public void createAccount(AccountCreateContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.AccountCreateContract, responseObserver);
		}

		@Override
		public void updateWitness(Contract.WitnessUpdateContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.WitnessUpdateContract, responseObserver);
		}

		@Override
		public void setAccountId(Contract.SetAccountIdContract request,
								 StreamObserver<Transaction> responseObserver) {
			try {
				responseObserver.onNext(
					createTransactionCapsule(request, ContractType.SetAccountIdContract).getInstance());
			} catch (ContractValidateException e) {
				responseObserver
					.onNext(null);
				logger.debug("ContractValidateException: {}", e.getMessage());
			}
			responseObserver.onCompleted();
		}

		@Override
		public void updateAccount(Contract.AccountUpdateContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.AccountUpdateContract, responseObserver);
		}

		@Override
		public void updateAsset(Contract.UpdateAssetContract request,
								StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.UpdateAssetContract, responseObserver);
		}

		@Override
		public void freezeBalance(Contract.FreezeBalanceContract request,
								  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.FreezeBalanceContract, responseObserver);
		}

		@Override
		public void unfreezeBalance(Contract.UnfreezeBalanceContract request,
									StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.UnfreezeBalanceContract, responseObserver);
		}

		@Override
		public void withdrawBalance(Contract.WithdrawBalanceContract request,
									StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.WithdrawBalanceContract, responseObserver);
		}

		@Override
		public void proposalCreate(Contract.ProposalCreateContract request,
								   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ProposalCreateContract, responseObserver);
		}


		@Override
		public void proposalApprove(Contract.ProposalApproveContract request,
									StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ProposalApproveContract, responseObserver);
		}

		@Override
		public void proposalDelete(Contract.ProposalDeleteContract request,
								   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ProposalDeleteContract, responseObserver);
		}

//    @Override
//    public void buyStorage(Contract.BuyStorageContract request,
//        StreamObserver<TransactionExtension> responseObserver) {
//      createTransactionExtension(request, ContractType.BuyStorageContract, responseObserver);
//    }
//
//    @Override
//    public void buyStorageBytes(Contract.BuyStorageBytesContract request,
//        StreamObserver<TransactionExtension> responseObserver) {
//      createTransactionExtension(request, ContractType.BuyStorageBytesContract, responseObserver);
//    }
//
//    @Override
//    public void sellStorage(Contract.SellStorageContract request,
//        StreamObserver<TransactionExtension> responseObserver) {
//      createTransactionExtension(request, ContractType.SellStorageContract, responseObserver);
//    }

		@Override
		public void exchangeCreate(Contract.ExchangeCreateContract request,
								   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ExchangeCreateContract, responseObserver);
		}


		@Override
		public void exchangeInject(Contract.ExchangeInjectContract request,
								   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ExchangeInjectContract, responseObserver);
		}

		@Override
		public void exchangeWithdraw(Contract.ExchangeWithdrawContract request,
									 StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ExchangeWithdrawContract, responseObserver);
		}

		@Override
		public void exchangeTransaction(Contract.ExchangeTransactionContract request,
										StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ExchangeTransactionContract,
				responseObserver);
		}

		@Override
		public void getNowBlock(EmptyMessage request,
								StreamObserver<BlockExtension> responseObserver) {
			Block block = wallet.getNowBlock();
			responseObserver.onNext(block2Extension(block));
			responseObserver.onCompleted();
		}

		@Override
		public void getBlockByNum(NumberMessage request,
								  StreamObserver<BlockExtension> responseObserver) {
			Block block = wallet.getBlockByNum(request.getNum());
			responseObserver.onNext(block2Extension(block));
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionCountByBlockNum(NumberMessage request,
												  StreamObserver<NumberMessage> responseObserver) {
			NumberMessage.Builder builder = NumberMessage.newBuilder();
			try {
				Block block = dbManager.getBlockByNum(request.getNum()).getInstance();
				builder.setNum(block.getTransactionsCount());
			} catch (StoreException e) {
				logger.error(e.getMessage());
				builder.setNum(-1);
			}
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		}

		@Override
		public void listNodes(EmptyMessage request, StreamObserver<NodeList> responseObserver) {
			List<NodeHandler> handlerList = nodeManager.dumpActiveNodes();

			Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();
			for (NodeHandler handler : handlerList) {
				String key = handler.getNode().getHexId() + handler.getNode().getHost();
				nodeHandlerMap.put(key, handler);
			}

			NodeList.Builder nodeListBuilder = NodeList.newBuilder();

			nodeHandlerMap.entrySet().stream()
				.forEach(v -> {
					io.midasprotocol.common.overlay.discover.node.Node node = v.getValue().getNode();
					nodeListBuilder.addNodes(Node.newBuilder().setAddress(
						Address.newBuilder()
							.setHost(ByteString.copyFrom(ByteArray.fromString(node.getHost())))
							.setPort(node.getPort())));
				});

			responseObserver.onNext(nodeListBuilder.build());
			responseObserver.onCompleted();
		}

		@Override
		public void transferAsset(TransferAssetContract request,
								   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.TransferAssetContract, responseObserver);
		}

		@Override
		public void participateAssetIssue(ParticipateAssetIssueContract request,
										   StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.ParticipateAssetIssueContract,
				responseObserver);
		}

		@Override
		public void getAssetIssueByAccount(Account request,
										   StreamObserver<AssetIssueList> responseObserver) {
			ByteString fromBs = request.getAddress();

			if (fromBs != null) {
				responseObserver.onNext(wallet.getAssetIssueByAccount(fromBs));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAccountResource(Account request,
									   StreamObserver<AccountResourceMessage> responseObserver) {
			ByteString fromBs = request.getAddress();

			if (fromBs != null) {
				responseObserver.onNext(wallet.getAccountResource(fromBs));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueByName(BytesMessage request,
										StreamObserver<AssetIssueContract> responseObserver) {
			ByteString assetName = request.getValue();
			if (assetName != null) {
				try {
					responseObserver.onNext(wallet.getAssetIssueByName(assetName));
				} catch (NonUniqueObjectException e) {
					responseObserver.onNext(null);
					logger.debug("FullNode NonUniqueObjectException: {}", e.getMessage());
				}
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueListByName(BytesMessage request,
											StreamObserver<AssetIssueList> responseObserver) {
			ByteString assetName = request.getValue();

			if (assetName != null) {
				responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueById(BytesMessage request,
									  StreamObserver<AssetIssueContract> responseObserver) {
			ByteString assetId = request.getValue();

			if (assetId != null) {
				responseObserver.onNext(wallet.getAssetIssueById(Longs.fromByteArray(assetId.toByteArray())));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getBlockById(BytesMessage request, StreamObserver<Block> responseObserver) {
			ByteString blockId = request.getValue();

			if (Objects.nonNull(blockId)) {
				responseObserver.onNext(wallet.getBlockById(blockId));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getProposalById(BytesMessage request,
									StreamObserver<Proposal> responseObserver) {
			ByteString proposalId = request.getValue();

			if (Objects.nonNull(proposalId)) {
				responseObserver.onNext(wallet.getProposalById(proposalId));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getExchangeById(BytesMessage request,
									StreamObserver<Exchange> responseObserver) {
			ByteString exchangeId = request.getValue();

			if (Objects.nonNull(exchangeId)) {
				responseObserver.onNext(wallet.getExchangeById(exchangeId));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getBlockByLimitNext(BlockLimit request,
										 StreamObserver<BlockListExtension> responseObserver) {
			long startNum = request.getStartNum();
			long endNum = request.getEndNum();

			if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
				responseObserver
					.onNext(blocklist2Extention(wallet.getBlocksByLimitNext(startNum, endNum - startNum)));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getBlockByLatestNum(NumberMessage request,
										 StreamObserver<BlockListExtension> responseObserver) {
			long getNum = request.getNum();

			if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
				responseObserver.onNext(blocklist2Extention(wallet.getBlockByLatestNum(getNum)));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionById(BytesMessage request,
									   StreamObserver<Transaction> responseObserver) {
			ByteString transactionId = request.getValue();

			if (Objects.nonNull(transactionId)) {
				responseObserver.onNext(wallet.getTransactionById(transactionId));
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void deployContract(io.midasprotocol.protos.Contract.CreateSmartContract request,
								   io.grpc.stub.StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.CreateSmartContract, responseObserver);
		}

		public void totalTransaction(EmptyMessage request,
									 StreamObserver<NumberMessage> responseObserver) {
			responseObserver.onNext(wallet.totalTransaction());
			responseObserver.onCompleted();
		}

		@Override
		public void getNextMaintenanceTime(EmptyMessage request,
										   StreamObserver<NumberMessage> responseObserver) {
			responseObserver.onNext(wallet.getNextMaintenanceTime());
			responseObserver.onCompleted();
		}

		@Override
		public void getAssetIssueList(EmptyMessage request,
									  StreamObserver<AssetIssueList> responseObserver) {
			responseObserver.onNext(wallet.getAssetIssueList());
			responseObserver.onCompleted();
		}

		@Override
		public void triggerContract(Contract.TriggerSmartContract request,
									StreamObserver<TransactionExtension> responseObserver) {
			TransactionExtension.Builder trxExtBuilder = TransactionExtension.newBuilder();
			Return.Builder retBuilder = Return.newBuilder();
			try {
				TransactionCapsule trxCap = createTransactionCapsule(request,
					ContractType.TriggerSmartContract);
				Transaction trx = wallet.triggerContract(request, trxCap, trxExtBuilder, retBuilder);
				trxExtBuilder.setTransaction(trx);
				trxExtBuilder.setTxId(trxCap.getTransactionId().getByteString());
				retBuilder.setResult(true).setCode(ResponseCode.SUCCESS);
				trxExtBuilder.setResult(retBuilder);
			} catch (ContractValidateException | VMIllegalException e) {
				retBuilder.setResult(false).setCode(ResponseCode.CONTRACT_VALIDATE_ERROR)
					.setMessage(ByteString.copyFromUtf8("contract validate error : " + e.getMessage()));
				trxExtBuilder.setResult(retBuilder);
				logger.warn("ContractValidateException: {}", e.getMessage());
			} catch (RuntimeException e) {
				retBuilder.setResult(false).setCode(ResponseCode.CONTRACT_EXE_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				trxExtBuilder.setResult(retBuilder);
				logger.warn("When run constant call in VM, have RuntimeException: " + e.getMessage());
			} catch (Exception e) {
				retBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
					.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
				trxExtBuilder.setResult(retBuilder);
				logger.warn("unknown exception caught: " + e.getMessage(), e);
			} finally {
				responseObserver.onNext(trxExtBuilder.build());
				responseObserver.onCompleted();
			}
		}

		public void getPaginatedAssetIssueList(PaginatedMessage request,
											   StreamObserver<AssetIssueList> responseObserver) {
			responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
			responseObserver.onCompleted();
		}

		@Override
		public void getContract(BytesMessage request,
								StreamObserver<Protocol.SmartContract> responseObserver) {
			Protocol.SmartContract contract = wallet.getContract(request);
			responseObserver.onNext(contract);
			responseObserver.onCompleted();
		}

		public void listWitnesses(EmptyMessage request,
								  StreamObserver<WitnessList> responseObserver) {
			responseObserver.onNext(wallet.getWitnessList());
			responseObserver.onCompleted();
		}

		@Override
		public void listProposals(EmptyMessage request,
								  StreamObserver<ProposalList> responseObserver) {
			responseObserver.onNext(wallet.getProposalList());
			responseObserver.onCompleted();
		}


		@Override
		public void getDelegatedResource(DelegatedResourceMessage request,
										 StreamObserver<DelegatedResourceList> responseObserver) {
			responseObserver
				.onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
			responseObserver.onCompleted();
		}

		public void getDelegatedResourceAccountIndex(BytesMessage request,
													 StreamObserver<io.midasprotocol.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
			responseObserver
				.onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
			responseObserver.onCompleted();
		}

		@Override
		public void getPaginatedProposalList(PaginatedMessage request,
											 StreamObserver<ProposalList> responseObserver) {
			responseObserver
				.onNext(wallet.getPaginatedProposalList(request.getOffset(), request.getLimit()));
			responseObserver.onCompleted();

		}

		@Override
		public void getPaginatedExchangeList(PaginatedMessage request,
											 StreamObserver<ExchangeList> responseObserver) {
			responseObserver
				.onNext(wallet.getPaginatedExchangeList(request.getOffset(), request.getLimit()));
			responseObserver.onCompleted();

		}

		@Override
		public void listExchanges(EmptyMessage request,
								  StreamObserver<ExchangeList> responseObserver) {
			responseObserver.onNext(wallet.getExchangeList());
			responseObserver.onCompleted();
		}

		@Override
		public void getChainParameters(EmptyMessage request,
									   StreamObserver<Protocol.ChainParameters> responseObserver) {
			responseObserver.onNext(wallet.getChainParameters());
			responseObserver.onCompleted();
		}

		@Override
		public void generateAddress(EmptyMessage request,
									StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
			ECKey ecKey = new ECKey(Utils.getRandom());
			byte[] priKey = ecKey.getPrivKeyBytes();
			byte[] address = ecKey.getAddress();
			String addressStr = Wallet.encodeBase58Check(address);
			String priKeyStr = Hex.encodeHexString(priKey);
			AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
			builder.setAddress(addressStr);
			builder.setPrivateKey(priKeyStr);
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		}

		@Override
		public void getTransactionInfoById(BytesMessage request,
										   StreamObserver<TransactionInfo> responseObserver) {
			ByteString id = request.getValue();
			if (null != id) {
				TransactionInfo reply = wallet.getTransactionInfoById(id);

				responseObserver.onNext(reply);
			} else {
				responseObserver.onNext(null);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void getNodeInfo(EmptyMessage request, StreamObserver<NodeInfo> responseObserver) {
			try {
				responseObserver.onNext(nodeInfoService.getNodeInfo().transferToProtoEntity());
			} catch (Exception e) {
				responseObserver.onError(e);
			}
			responseObserver.onCompleted();
		}

		@Override
		public void accountPermissionUpdate(AccountPermissionUpdateContract request,
											StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.AccountPermissionUpdateContract,
				responseObserver);
		}

		@Override
		public void getBlockReward(NumberMessage request, StreamObserver<BlockRewardList> responseObserver) {
			responseObserver.onNext(wallet.getBlockReward(request.getNum()));
			responseObserver.onCompleted();
		}

		@Override
		public void stake(Contract.StakeContract request,
						  StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.StakeContract, responseObserver);
		}

		@Override
		public void unstake(Contract.UnstakeContract request,
							StreamObserver<TransactionExtension> responseObserver) {
			createTransactionExtension(request, ContractType.UnstakeContract, responseObserver);
		}
	}
}
