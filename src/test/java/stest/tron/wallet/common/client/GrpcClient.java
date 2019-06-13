package stest.tron.wallet.common.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.*;
import io.midasprotocol.api.WalletExtensionGrpc;
import io.midasprotocol.api.WalletGrpc;
import io.midasprotocol.api.WalletSolidityGrpc;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.FreezeBalanceContract;
import io.midasprotocol.protos.Contract.UnfreezeBalanceContract;
import io.midasprotocol.protos.Contract.WithdrawBalanceContract;
import io.midasprotocol.protos.Protocol.Account;
import io.midasprotocol.protos.Protocol.Block;
import io.midasprotocol.protos.Protocol.Transaction;

import java.util.Optional;
import java.util.concurrent.TimeUnit;


public class GrpcClient {

	private ManagedChannel channelFull = null;
	private ManagedChannel channelSolidity = null;
	private WalletGrpc.WalletBlockingStub blockingStubFull = null;
	private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
	private WalletExtensionGrpc.WalletExtensionBlockingStub blockingStubExtension = null;

	//  public GrpcClient(String host, int port) {
	//    channel = ManagedChannelBuilder.forAddress(host, port)
	//        .usePlaintext(true)
	//        .build();
	//    blockingStub = WalletGrpc.newBlockingStub(channel);
	//  }

	/**
	 * constructor.
	 */

	public GrpcClient(String fullnode, String soliditynode) {
		if (!(fullnode.isEmpty())) {
			channelFull = ManagedChannelBuilder.forTarget(fullnode)
					.usePlaintext(true)
					.build();
			blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
		}
		if (!(soliditynode.isEmpty())) {
			channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
					.usePlaintext(true)
					.build();
			blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
			blockingStubExtension = WalletExtensionGrpc.newBlockingStub(channelSolidity);
		}
	}

	/**
	 * constructor.
	 */

	public void shutdown() throws InterruptedException {
		if (channelFull != null) {
			channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
		if (channelSolidity != null) {
			channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	/**
	 * constructor.
	 */

	public Account queryAccount(byte[] address) {
		ByteString addressBs = ByteString.copyFrom(address);
		Account request = Account.newBuilder().setAddress(addressBs).build();
		if (blockingStubSolidity != null) {
			return blockingStubSolidity.getAccount(request);
		} else {
			return blockingStubFull.getAccount(request);
		}
	}

	public TransactionExtension createTransaction(Contract.AccountUpdateContract contract) {
		return blockingStubFull.updateAccount(contract);
	}

	public TransactionExtension createTransaction(Contract.TransferContract contract) {
		return blockingStubFull.createTransaction(contract);
	}

	public TransactionExtension createTransaction(FreezeBalanceContract contract) {
		return blockingStubFull.freezeBalance(contract);
	}

	public TransactionExtension createTransaction(WithdrawBalanceContract contract) {
		return blockingStubFull.withdrawBalance(contract);
	}

	public TransactionExtension createTransaction(UnfreezeBalanceContract contract) {
		return blockingStubFull.unfreezeBalance(contract);
	}

	public TransactionExtension createTransferAssetTransaction(Contract.TransferAssetContract contract) {
		return blockingStubFull.transferAsset(contract);
	}

	public TransactionExtension createParticipateAssetIssueTransaction(
			Contract.ParticipateAssetIssueContract contract) {
		return blockingStubFull.participateAssetIssue(contract);
	}

	public TransactionExtension createAccount(Contract.AccountCreateContract contract) {
		return blockingStubFull.createAccount(contract);
	}

	public TransactionExtension createAssetIssue(Contract.AssetIssueContract contract) {
		return blockingStubFull.createAssetIssue(contract);
	}

	public TransactionExtension voteWitnessAccount(Contract.VoteWitnessContract contract) {
		return blockingStubFull.voteWitnessAccount(contract);
	}

	public TransactionExtension createWitness(Contract.WitnessCreateContract contract) {
		return blockingStubFull.createWitness(contract);
	}

	public boolean broadcastTransaction(Transaction signaturedTransaction) {
		GrpcAPI.Return response = blockingStubFull.broadcastTransaction(signaturedTransaction);
		return response.getResult();
	}

	/**
	 * constructor.
	 */

	public AccountNetMessage getAccountNet(byte[] address) {
		ByteString addressBs = ByteString.copyFrom(address);
		Account request = Account.newBuilder().setAddress(addressBs).build();
		return blockingStubFull.getAccountNet(request);
	}

	/**
	 * constructor.
	 */

	public BlockExtension getBlock(long blockNum) {
		if (blockNum < 0) {
			if (blockingStubSolidity != null) {
				return blockingStubSolidity.getNowBlock(EmptyMessage.newBuilder().build());
			} else {
				return blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
			}
		}
		NumberMessage.Builder builder = NumberMessage.newBuilder();
		builder.setNum(blockNum);
		if (blockingStubSolidity != null) {
			return blockingStubSolidity.getBlockByNum(builder.build());
		} else {
			return blockingStubFull.getBlockByNum(builder.build());
		}
	}

  /*    public Optional<AccountList> listAccounts() {
        if(blockingStubSolidity != null) {
            AccountList accountList = blockingStubSolidity.listAccounts(
            EmptyMessage.newBuilder().build());
            return Optional.ofNullable(accountList);
        }else{
            AccountList accountList = blockingStubFull.listAccounts(
            EmptyMessage.newBuilder().build());
            return Optional.ofNullable(accountList);
        }
    }*/

	/**
	 * constructor.
	 */
	public Optional<WitnessList> listWitnesses() {
		if (blockingStubSolidity != null) {
			WitnessList witnessList = blockingStubSolidity.listWitnesses(
					EmptyMessage.newBuilder().build());
			return Optional.ofNullable(witnessList);
		} else {
			WitnessList witnessList = blockingStubFull.listWitnesses(
					EmptyMessage.newBuilder().build());
			return Optional.ofNullable(witnessList);
		}
	}

	/**
	 * constructor.
	 */

	public Optional<AssetIssueList> getAssetIssueList(long offset, long limit) {
		PaginatedMessage.Builder pageMessageBuilder = PaginatedMessage.newBuilder();
		pageMessageBuilder.setOffset(offset);
		pageMessageBuilder.setLimit(limit);
		if (blockingStubSolidity != null) {
			AssetIssueList assetIssueList = blockingStubSolidity
					.getPaginatedAssetIssueList(pageMessageBuilder.build());
			return Optional.ofNullable(assetIssueList);
		} else {
			AssetIssueList assetIssueList = blockingStubFull
					.getPaginatedAssetIssueList(pageMessageBuilder.build());
			return Optional.ofNullable(assetIssueList);
		}
	}


	/**
	 * constructor.
	 */

	public Optional<AssetIssueList> getAssetIssueList() {
		if (blockingStubSolidity != null) {
			AssetIssueList assetIssueList = blockingStubSolidity
					.getAssetIssueList(EmptyMessage.newBuilder().build());
			return Optional.ofNullable(assetIssueList);
		} else {
			AssetIssueList assetIssueList = blockingStubFull
					.getAssetIssueList(EmptyMessage.newBuilder().build());
			return Optional.ofNullable(assetIssueList);
		}
	}

	/**
	 * constructor.
	 */

	public Optional<NodeList> listNodes() {
		NodeList nodeList = blockingStubFull
				.listNodes(EmptyMessage.newBuilder().build());
		return Optional.ofNullable(nodeList);
	}

  /*  public Optional<AssetIssueList> getAssetIssueByAccount(byte[] address) {
      ByteString addressBs = ByteString.copyFrom(address);
      Account request = Account.newBuilder().setAddress(addressBs).build();
      if(blockingStubSolidity != null) {
          AssetIssueList assetIssueList = blockingStubSolidity
                  .getAssetIssueByAccount(request);
          return Optional.ofNullable(assetIssueList);
      } else {
          AssetIssueList assetIssueList = blockingStubFull
                  .getAssetIssueByAccount(request);
          return Optional.ofNullable(assetIssueList);
      }
  }*/
  /*  public AssetIssueContract getAssetIssueByName(String assetName) {
      ByteString assetNameBs = ByteString.copyFrom(assetName.getBytes());
      BytesMessage request = BytesMessage.newBuilder().setValue(assetNameBs).build();
      if(blockingStubSolidity != null) {
          return blockingStubSolidity.getAssetIssueByName(request);
      } else {
          return blockingStubFull.getAssetIssueByName(request);
      }
   }*/

  /*  public NumberMessage getTotalTransaction() {
      if(blockingStubSolidity != null) {
          return blockingStubSolidity.totalTransaction(EmptyMessage.newBuilder().build());
      } else {
          return blockingStubFull.totalTransaction(EmptyMessage.newBuilder().build());
      }
   }*/

  /*    public Optional<AssetIssueList> getAssetIssueListByTimestamp(long time) {
        NumberMessage.Builder timeStamp = NumberMessage.newBuilder();
        timeStamp.setNum(time);
        AssetIssueList assetIssueList = blockingStubSolidity
        .getAssetIssueListByTimestamp(timeStamp.build());
        return Optional.ofNullable(assetIssueList);
    }*/
  /*    public Optional<TransactionList> getTransactionsByTimestamp(
        long start, long end, int offset , int limit) {
        TimeMessage.Builder timeMessage = TimeMessage.newBuilder();
        timeMessage.setBeginInMilliseconds(start);
        timeMessage.setEndInMilliseconds(end);
        TimePaginatedMessage.Builder timePageMessage = TimePaginatedMessage.newBuilder();
        timePageMessage.setTimeMessage(timeMessage);
        timePageMessage.setOffset(offset);
        timePageMessage.setLimit(limit);
        TransactionList transactionList = blockingStubExtension
        .getTransactionsByTimestamp(timePageMessage.build());
        return Optional.ofNullable(transactionList);
    }*/

	/**
	 * constructor.
	 */

	public Optional<TransactionListExtension> getTransactionsFromThis(byte[] address) {
		ByteString addressBs = ByteString.copyFrom(address);
		Account account = Account.newBuilder().setAddress(addressBs).build();
		AccountPaginated.Builder builder = AccountPaginated.newBuilder().setAccount(account);
		builder.setLimit(1000);
		builder.setOffset(0);
		TransactionListExtension transactionList = blockingStubExtension
				.getTransactionsFromThis(builder.build());
		return Optional.ofNullable(transactionList);
	}

	/**
	 * constructor.
	 */

	public Optional<TransactionListExtension> getTransactionsToThis(byte[] address) {
		ByteString addressBs = ByteString.copyFrom(address);
		Account account = Account.newBuilder().setAddress(addressBs).build();
		AccountPaginated.Builder builder = AccountPaginated.newBuilder().setAccount(account);
		builder.setLimit(1000);
		builder.setOffset(0);
		TransactionListExtension transactionList = blockingStubExtension.getTransactionsToThis(builder.build());
		return Optional.ofNullable(transactionList);
	}

  /*    public Optional<Transaction> getTransactionById(String txID){
        ByteString bsTxid = ByteString.copyFrom(ByteArray.fromHexString(txID));
        BytesMessage request = BytesMessage.newBuilder().setValue(bsTxid).build();
        if(blockingStubSolidity != null) {
            Transaction transaction = blockingStubSolidity.getTransactionById(request);
            return Optional.ofNullable(transaction);
        } else {
            Transaction transaction = blockingStubFull.getTransactionById(request);
            return Optional.ofNullable(transaction);
        }
   }*/


	/**
	 * constructor.
	 */

	public Optional<Block> getBlockById(String blockId) {
		ByteString bsTxid = ByteString.copyFrom(ByteArray.fromHexString(blockId));
		BytesMessage request = BytesMessage.newBuilder().setValue(bsTxid).build();
		Block block = blockingStubFull.getBlockById(request);
		return Optional.ofNullable(block);
	}

	/**
	 * constructor.
	 */

	public Optional<BlockListExtension> getBlockByLimitNext(long start, long end) {
		BlockLimit.Builder builder = BlockLimit.newBuilder();
		builder.setStartNum(start);
		builder.setEndNum(end);
		BlockListExtension blockList = blockingStubFull.getBlockByLimitNext(builder.build());
		return Optional.ofNullable(blockList);
	}

	/**
	 * constructor.
	 */

	public Optional<BlockListExtension> getBlockByLatestNum(long num) {
		NumberMessage numberMessage = NumberMessage.newBuilder().setNum(num).build();
		BlockListExtension blockList = blockingStubFull.getBlockByLatestNum(numberMessage);
		return Optional.ofNullable(blockList);
	}
}
