/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package io.midasprotocol.common.runtime.vm.program;

import io.midasprotocol.common.runtime.vm.DataWord;
import io.midasprotocol.common.runtime.vm.program.invoke.ProgramInvoke;
import io.midasprotocol.common.runtime.vm.program.listener.ProgramListener;
import io.midasprotocol.common.runtime.vm.program.listener.ProgramListenerAware;
import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.common.storage.Key;
import io.midasprotocol.common.storage.Value;
import io.midasprotocol.core.capsule.*;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.AccountType;

public class ContractState implements Deposit, ProgramListenerAware {

	// contract address
	private final DataWord address;
	private Deposit deposit;
	private ProgramListener programListener;

	ContractState(ProgramInvoke programInvoke) {
		this.address = programInvoke.getContractAddress();
		this.deposit = programInvoke.getDeposit();
	}

	@Override
	public Manager getDbManager() {
		return deposit.getDbManager();
	}

	@Override
	public void setProgramListener(ProgramListener listener) {
		this.programListener = listener;
	}

	@Override
	public AccountCapsule createAccount(byte[] addr, Protocol.AccountType type) {
		return deposit.createAccount(addr, type);
	}

	@Override
	public AccountCapsule createAccount(byte[] address, String accountName, AccountType type) {
		return deposit.createAccount(address, accountName, type);
	}


	@Override
	public AccountCapsule getAccount(byte[] addr) {
		return deposit.getAccount(addr);
	}

	@Override
	public WitnessCapsule getWitness(byte[] address) {
		return deposit.getWitness(address);
	}

	@Override
	public VoteChangeCapsule getVoteChangeCapsule(byte[] address) {
		return deposit.getVoteChangeCapsule(address);
	}

	@Override
	public ProposalCapsule getProposalCapsule(byte[] id) {
		return deposit.getProposalCapsule(id);
	}

	@Override
	public BytesCapsule getDynamic(byte[] bytesKey) {
		return deposit.getDynamic(bytesKey);
	}

	@Override
	public StakeChangeCapsule getStakeChangeCapsule(byte[] address) {
		return deposit.getStakeChangeCapsule(address);
	}

	@Override
	public StakeAccountCapsule getStakeAccountCapsule(byte[] address) {
		return deposit.getStakeAccountCapsule(address);
	}

	@Override
	public void deleteContract(byte[] address) {
		deposit.deleteContract(address);
	}

	@Override
	public void createContract(byte[] codeHash, ContractCapsule contractCapsule) {
		deposit.createContract(codeHash, contractCapsule);
	}

	@Override
	public ContractCapsule getContract(byte[] codeHash) {
		return deposit.getContract(codeHash);
	}

	@Override
	public void saveCode(byte[] addr, byte[] code) {
		deposit.saveCode(addr, code);
	}

	@Override
	public byte[] getCode(byte[] addr) {
		return deposit.getCode(addr);
	}

	@Override
	public void putStorageValue(byte[] addr, DataWord key, DataWord value) {
		if (canListenTrace(addr)) {
			programListener.onStoragePut(key, value);
		}
		deposit.putStorageValue(addr, key, value);
	}

	private boolean canListenTrace(byte[] address) {
		return (programListener != null) && this.address.equals(new DataWord(address));
	}

	@Override
	public DataWord getStorageValue(byte[] addr, DataWord key) {
		return deposit.getStorageValue(addr, key);
	}

	@Override
	public long getBalance(byte[] addr) {
		return deposit.getBalance(addr);
	}

	@Override
	public long addBalance(byte[] addr, long value) {
		return deposit.addBalance(addr, value);
	}

	@Override
	public Deposit newDepositChild() {
		return deposit.newDepositChild();
	}

	@Override
	public void commit() {
		deposit.commit();
	}

	@Override
	public Storage getStorage(byte[] address) {
		return deposit.getStorage(address);
	}

	@Override
	public void putAccount(Key key, Value value) {
		deposit.putAccount(key, value);
	}

	@Override
	public void putTransaction(Key key, Value value) {
		deposit.putTransaction(key, value);
	}

	@Override
	public void putBlock(Key key, Value value) {
		deposit.putBlock(key, value);
	}

	@Override
	public void putWitness(Key key, Value value) {
		deposit.putWitness(key, value);
	}

	@Override
	public void putCode(Key key, Value value) {
		deposit.putCode(key, value);
	}

	@Override
	public void putContract(Key key, Value value) {
		deposit.putContract(key, value);
	}

	@Override
	public void putStorage(Key key, Storage cache) {
		deposit.putStorage(key, cache);
	}

	@Override
	public void putVotes(Key key, Value value) {
		deposit.putVotes(key, value);
	}

	@Override
	public void putProposal(Key key, Value value) {
		deposit.putProposal(key, value);
	}

	@Override
	public void putDynamicProperties(Key key, Value value) {
		deposit.putDynamicProperties(key, value);
	}

	@Override
	public void putStakeChange(Key key, Value value) {
		deposit.putStakeChange(key, value);
	}

	@Override
	public void putStakeAccount(Key key, Value value) {
		deposit.putStakeAccount(key, value);
	}

	@Override
	public void setParent(Deposit deposit) {
		this.deposit.setParent(deposit);
	}

	@Override
	public TransactionCapsule getTransaction(byte[] trxHash) {
		return this.deposit.getTransaction(trxHash);
	}

	@Override
	public void putAccountValue(byte[] address, AccountCapsule accountCapsule) {
		this.deposit.putAccountValue(address, accountCapsule);
	}

	@Override
	public void putVoteChangeValue(byte[] address, VoteChangeCapsule voteChangeCapsule) {
		this.deposit.putVoteChangeValue(address, voteChangeCapsule);
	}

	@Override
	public void putProposalValue(byte[] address, ProposalCapsule proposalCapsule) {
		deposit.putProposalValue(address, proposalCapsule);
	}

	@Override
	public void putStakeChangeValue(byte[] address, StakeChangeCapsule stakeChangeCapsule) {
		deposit.putStakeChangeValue(address, stakeChangeCapsule);
	}

	@Override
	public void putStakeAccountValue(byte[] address, StakeAccountCapsule stakeAccountCapsule) {
		deposit.putStakeAccountValue(address, stakeAccountCapsule);
	}

	@Override
	public void putDynamicPropertiesWithLatestProposalNum(long num) {
		deposit.putDynamicPropertiesWithLatestProposalNum(num);
	}

	@Override
	public long getLatestProposalNum() {
		return deposit.getLatestProposalNum();
	}

	@Override
	public long getWitnessAllowanceFrozenTime() {
		return deposit.getWitnessAllowanceFrozenTime();
	}

	@Override
	public long getMaintenanceTimeInterval() {
		return deposit.getMaintenanceTimeInterval();
	}

	@Override
	public long getNextMaintenanceTime() {
		return deposit.getNextMaintenanceTime();
	}

	@Override
	public long addTokenBalance(byte[] address, long tokenId, long value) {
		return deposit.addTokenBalance(address, tokenId, value);
	}

	@Override
	public long getTokenBalance(byte[] address, long tokenId) {
		return deposit.getTokenBalance(address, tokenId);
	}

	@Override
	public AssetIssueCapsule getAssetIssue(long tokenId) {
		return deposit.getAssetIssue(tokenId);
	}

	@Override
	public BlockCapsule getBlock(byte[] blockHash) {
		return this.deposit.getBlock(blockHash);
	}

	@Override
	public byte[] getBlackHoleAddress() {
		return deposit.getBlackHoleAddress();
	}

}
