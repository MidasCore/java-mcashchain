package io.midasprotocol.common.storage;

import io.midasprotocol.common.runtime.vm.DataWord;
import io.midasprotocol.common.runtime.vm.program.Storage;
import io.midasprotocol.core.capsule.*;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol;

public interface Deposit {

	Manager getDbManager();

	AccountCapsule createAccount(byte[] address, Protocol.AccountType type);

	AccountCapsule createAccount(byte[] address, String accountName, Protocol.AccountType type);

	AccountCapsule getAccount(byte[] address);

	WitnessCapsule getWitness(byte[] address);

	VoteChangeCapsule getVoteChangeCapsule(byte[] address);

	ProposalCapsule getProposalCapsule(byte[] id);

	BytesCapsule getDynamic(byte[] bytesKey);

	StakeChangeCapsule getStakeChangeCapsule(byte[] address);

	StakeAccountCapsule getStakeAccountCapsule(byte[] address);

	void deleteContract(byte[] address);

	void createContract(byte[] address, ContractCapsule contractCapsule);

	ContractCapsule getContract(byte[] address);

	void saveCode(byte[] codeHash, byte[] code);

	byte[] getCode(byte[] codeHash);

	void putStorageValue(byte[] address, DataWord key, DataWord value);

	DataWord getStorageValue(byte[] address, DataWord key);

	Storage getStorage(byte[] address);

	long getBalance(byte[] address);

	long addBalance(byte[] address, long value);

	Deposit newDepositChild();

	void setParent(Deposit deposit);

	void commit();

	void putAccount(Key key, Value value);

	void putTransaction(Key key, Value value);

	void putBlock(Key key, Value value);

	void putWitness(Key key, Value value);

	void putCode(Key key, Value value);

	void putContract(Key key, Value value);

	void putStorage(Key key, Storage cache);

	void putVotes(Key key, Value value);

	void putProposal(Key key, Value value);

	void putDynamicProperties(Key key, Value value);

	void putStakeChange(Key key, Value value);

	void putStakeAccount(Key key, Value value);

	void putAccountValue(byte[] address, AccountCapsule accountCapsule);

	void putVoteChangeValue(byte[] address, VoteChangeCapsule voteChangeCapsule);

	void putProposalValue(byte[] address, ProposalCapsule proposalCapsule);

	void putStakeChangeValue(byte[] address, StakeChangeCapsule stakeChangeCapsule);

	void putStakeAccountValue(byte[] address, StakeAccountCapsule stakeAccountCapsule);

	void putDynamicPropertiesWithLatestProposalNum(long num);

	long getLatestProposalNum();

	long getWitnessAllowanceFrozenTime();

	long getMaintenanceTimeInterval();

	long getNextMaintenanceTime();

	long addTokenBalance(byte[] address, byte[] tokenId, long value);

	long getTokenBalance(byte[] address, byte[] tokenId);

	AssetIssueCapsule getAssetIssue(byte[] tokenId);

	TransactionCapsule getTransaction(byte[] trxHash);

	BlockCapsule getBlock(byte[] blockHash);

	byte[] getBlackHoleAddress();

}
