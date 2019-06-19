/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.midasprotocol.core.capsule;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.config.NodeTier;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Contract.AccountCreateContract;
import io.midasprotocol.protos.Contract.AccountUpdateContract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.*;
import io.midasprotocol.protos.Protocol.Account.AccountResource;
import io.midasprotocol.protos.Protocol.Permission.PermissionType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static io.midasprotocol.core.config.Parameter.NodeConstant.NODE_TIERS;

@Slf4j(topic = "capsule")
public class AccountCapsule implements ProtoCapsule<Account>, Comparable<AccountCapsule> {

	private Account account;


	/**
	 * get account from bytes data.
	 */
	public AccountCapsule(byte[] data) {
		try {
			this.account = Account.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage());
		}
	}

	/**
	 * initial account capsule.
	 */
	public AccountCapsule(ByteString accountName, ByteString address, AccountType accountType,
						  long balance) {
		this.account = Account.newBuilder()
			.setAccountName(accountName)
			.setType(accountType)
			.setAddress(address)
			.setBalance(balance)
			.build();
	}

	/**
	 * construct account from AccountCreateContract.
	 */
	public AccountCapsule(final AccountCreateContract contract) {
		this.account = Account.newBuilder()
			.setType(contract.getType())
			.setAddress(contract.getAccountAddress())
			.setTypeValue(contract.getTypeValue())
			.build();
	}

	/**
	 * construct account from AccountCreateContract and createTime.
	 */
	public AccountCapsule(final AccountCreateContract contract, long createTime,
						  boolean withDefaultPermission, Manager manager) {
		if (withDefaultPermission) {
			Permission owner = createDefaultOwnerPermission(contract.getAccountAddress());
			Permission active = createDefaultActivePermission(contract.getAccountAddress(), manager);

			this.account = Account.newBuilder()
				.setType(contract.getType())
				.setAddress(contract.getAccountAddress())
				.setTypeValue(contract.getTypeValue())
				.setCreateTime(createTime)
				.setOwnerPermission(owner)
				.addActivePermission(active)
				.build();
		} else {
			this.account = Account.newBuilder()
				.setType(contract.getType())
				.setAddress(contract.getAccountAddress())
				.setTypeValue(contract.getTypeValue())
				.setCreateTime(createTime)
				.build();
		}
	}


	/**
	 * construct account from AccountUpdateContract
	 */
	public AccountCapsule(final AccountUpdateContract contract) {

	}

	/**
	 * get account from address and account name.
	 */
	public AccountCapsule(ByteString address, ByteString accountName,
						  AccountType accountType) {
		this.account = Account.newBuilder()
			.setType(accountType)
			.setAccountName(accountName)
			.setAddress(address)
			.build();
	}

	/**
	 * get account from address.
	 */
	public AccountCapsule(ByteString address,
						  AccountType accountType) {
		this.account = Account.newBuilder()
			.setType(accountType)
			.setAddress(address)
			.build();
	}

	/**
	 * get account from address.
	 */
	public AccountCapsule(ByteString address,
						  AccountType accountType, long createTime,
						  boolean withDefaultPermission, Manager manager) {
		if (withDefaultPermission) {
			Permission owner = createDefaultOwnerPermission(address);
			Permission active = createDefaultActivePermission(address, manager);

			this.account = Account.newBuilder()
				.setType(accountType)
				.setAddress(address)
				.setCreateTime(createTime)
				.setOwnerPermission(owner)
				.addActivePermission(active)
				.build();
		} else {
			this.account = Account.newBuilder()
				.setType(accountType)
				.setAddress(address)
				.setCreateTime(createTime)
				.build();
		}

	}

	public AccountCapsule(Account account) {
		this.account = account;
	}

	private static ByteString getActiveDefaultOperations(Manager manager) {
		return ByteString.copyFrom(manager.getDynamicPropertiesStore().getActiveDefaultOperations());
	}

	public static Permission createDefaultOwnerPermission(ByteString address) {
		Key.Builder key = Key.newBuilder();
		key.setAddress(address);
		key.setWeight(1);

		Permission.Builder owner = Permission.newBuilder();
		owner.setType(PermissionType.Owner);
		owner.setId(0);
		owner.setPermissionName("owner");
		owner.setThreshold(1);
		owner.setParentId(0);
		owner.addKeys(key);

		return owner.build();
	}

	public static Permission createDefaultActivePermission(ByteString address, Manager manager) {
		Key.Builder key = Key.newBuilder();
		key.setAddress(address);
		key.setWeight(1);

		Permission.Builder active = Permission.newBuilder();
		active.setType(PermissionType.Active);
		active.setId(2);
		active.setPermissionName("active");
		active.setThreshold(1);
		active.setParentId(0);
		active.setOperations(getActiveDefaultOperations(manager));
		active.addKeys(key);

		return active.build();
	}

	public static Permission createDefaultWitnessPermission(ByteString address) {
		Key.Builder key = Key.newBuilder();
		key.setAddress(address);
		key.setWeight(1);

		Permission.Builder active = Permission.newBuilder();
		active.setType(PermissionType.Witness);
		active.setId(1);
		active.setPermissionName("witness");
		active.setThreshold(1);
		active.setParentId(0);
		active.addKeys(key);

		return active.build();
	}

	public static Permission getDefaultPermission(ByteString owner) {
		return createDefaultOwnerPermission(owner);
	}

	@Override
	public int compareTo(AccountCapsule otherObject) {
		return Long.compare(otherObject.getBalance(), this.getBalance());
	}

	public byte[] getData() {
		return this.account.toByteArray();
	}

	@Override
	public Account getInstance() {
		return this.account;
	}

	public void setInstance(Account account) {
		this.account = account;
	}

	public ByteString getAddress() {
		return this.account.getAddress();
	}

	public byte[] createDbKey() {
		return getAddress().toByteArray();
	}

	public String createReadableString() {
		return ByteArray.toHexString(getAddress().toByteArray());
	}

	public AccountType getType() {
		return this.account.getType();
	}

	public ByteString getAccountName() {
		return this.account.getAccountName();
	}

	/**
	 * set account name
	 */
	public void setAccountName(byte[] name) {
		this.account = this.account.toBuilder().setAccountName(ByteString.copyFrom(name)).build();
	}

	public ByteString getAccountId() {
		return this.account.getAccountId();
	}

	/**
	 * set account id
	 */
	public void setAccountId(byte[] id) {
		this.account = this.account.toBuilder().setAccountId(ByteString.copyFrom(id)).build();
	}

	public void setDefaultWitnessPermission(Manager manager) {
		Account.Builder builder = this.account.toBuilder();
		Permission witness = createDefaultWitnessPermission(this.getAddress());
		if (!this.account.hasOwnerPermission()) {
			Permission owner = createDefaultOwnerPermission(this.getAddress());
			builder.setOwnerPermission(owner);
		}
		if (this.account.getActivePermissionCount() == 0) {
			Permission active = createDefaultActivePermission(this.getAddress(), manager);
			builder.addActivePermission(active);
		}
		this.account = builder.setWitnessPermission(witness).build();
	}

	public byte[] getWitnessPermissionAddress() {
		if (this.account.getWitnessPermission().getKeysCount() == 0) {
			return getAddress().toByteArray();
		} else {
			return this.account.getWitnessPermission().getKeys(0).getAddress().toByteArray();
		}
	}

	public long getBalance() {
		return this.account.getBalance();
	}

	public void setBalance(long balance) {
		this.account = this.account.toBuilder().setBalance(balance).build();
	}

	public void setAccountResource(Account.AccountResource accountResource) {
		this.account = this.account.toBuilder().setAccountResource(accountResource).build();
	}

	public long getLatestOperationTime() {
		return this.account.getLatestOperationTime();
	}

	public void setLatestOperationTime(long latest_time) {
		this.account = this.account.toBuilder().setLatestOperationTime(latest_time).build();
	}

	public long getLatestBandwidthConsumeTime() {
		return this.account.getAccountResource().getLatestBandwidthConsumeTime();
	}

	public void setLatestBandwidthConsumeTime(long time) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().setLatestBandwidthConsumeTime(time).build();
		this.setAccountResource(newResource);
	}

	public long getLatestFreeBandwidthConsumeTime() {
		return this.account.getAccountResource().getLatestFreeBandwidthConsumeTime();
	}

	public void setLatestFreeBandwidthConsumeTime(long time) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().setLatestFreeBandwidthConsumeTime(time).build();
		this.setAccountResource(newResource);
	}

	public void addDelegatedFrozenBalanceForBandwidth(long balance) {
		long newBalance = this.account.getDelegatedFrozenForBandwidth().getDelegatedBalance() + balance;
		Account.DelegatedFrozen newDelegatedFrozen = this.account.getDelegatedFrozenForBandwidth()
			.toBuilder().setDelegatedBalance(newBalance).build();
		this.account = this.account.toBuilder().setDelegatedFrozenForBandwidth(newDelegatedFrozen).build();
	}

	public long getAcquiredDelegatedFrozenBalanceForBandwidth() {
		return this.account.getDelegatedFrozenForBandwidth().getAcquiredDelegatedBalance();
	}

	public void setAcquiredDelegatedFrozenBalanceForBandwidth(long balance) {
		Account.DelegatedFrozen newDelegated = this.account.getDelegatedFrozenForBandwidth()
			.toBuilder().setAcquiredDelegatedBalance(balance).build();
		this.account = this.account.toBuilder().setDelegatedFrozenForBandwidth(newDelegated).build();
	}

	public void addAcquiredDelegatedFrozenBalanceForBandwidth(long balance) {
		long newBalance = this.account.getDelegatedFrozenForBandwidth().getAcquiredDelegatedBalance() + balance;
		Account.DelegatedFrozen newDelegatedFrozen = this.account.getDelegatedFrozenForBandwidth()
			.toBuilder().setAcquiredDelegatedBalance(newBalance).build();
		this.account = this.account.toBuilder().setDelegatedFrozenForBandwidth(newDelegatedFrozen).build();
	}

	public long getAcquiredDelegatedFrozenBalanceForEnergy() {
		return this.account.getDelegatedFrozenForEnergy().getAcquiredDelegatedBalance();
	}

	public long getDelegatedFrozenBalanceForEnergy() {
		return this.account.getDelegatedFrozenForEnergy().getDelegatedBalance();
	}

	public long getDelegatedFrozenBalanceForBandwidth() {
		return this.account.getDelegatedFrozenForBandwidth().getDelegatedBalance();
	}

	public void setDelegatedFrozenBalanceForBandwidth(long balance) {
		Account.DelegatedFrozen newDelegated = this.account.getDelegatedFrozenForBandwidth()
			.toBuilder().setDelegatedBalance(balance).build();
		this.account = this.account.toBuilder().setDelegatedFrozenForBandwidth(newDelegated).build();
	}

	public void addAcquiredDelegatedFrozenBalanceForEnergy(long balance) {
		long newBalance = this.account.getDelegatedFrozenForEnergy().getAcquiredDelegatedBalance() + balance;
		Account.DelegatedFrozen newDelegatedFrozen = this.account.getDelegatedFrozenForEnergy()
			.toBuilder().setAcquiredDelegatedBalance(newBalance).build();
		this.account = this.account.toBuilder().setDelegatedFrozenForEnergy(newDelegatedFrozen).build();
	}

	public void addDelegatedFrozenBalanceForEnergy(long balance) {
		long newBalance = this.account.getDelegatedFrozenForEnergy().getDelegatedBalance() + balance;
		Account.DelegatedFrozen newDelegatedFrozen = this.account.getDelegatedFrozenForEnergy()
			.toBuilder().setDelegatedBalance(newBalance).build();
		this.account = this.account.toBuilder().setDelegatedFrozenForEnergy(newDelegatedFrozen).build();
	}

	@Override
	public String toString() {
		return this.account.toString();
	}

	/**
	 * set votes.
	 */
	public void setVote(ByteString voteAddress, long voteAdd) {
		this.account = this.account.toBuilder()
			.setVote(Vote.newBuilder().setVoteAddress(voteAddress).setVoteCount(voteAdd).build())
			.build();
	}

	public void clearAsset() {
		this.account = this.account.toBuilder()
			.clearAssets()
			.build();
	}

	public void clearLatestAssetOperationTime() {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().clearLatestAssetOperationTime().build();
		this.setAccountResource(newResource);
	}

	public void clearFreeBandwidthNetUsage() {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().clearAssetFreeBandwidthUsage().build();
		this.setAccountResource(newResource);
	}

	public void clearVote() {
		this.account = this.account.toBuilder().clearVote().build();
	}

	/**
	 * get votes.
	 */
	public Vote getVote() {
		if (this.account.hasVote()) {
			return this.account.getVote();
		} else {
			return null;
		}
	}

	public long getVotingPower() {
		long stakeAmount = getTotalStakeAmount();
		for (NodeTier tier : NODE_TIERS) {
			if (stakeAmount >= tier.getStakeAmount()) {
				return tier.getVotingPower();
			}
		}
		return 0;
	}

	public boolean assetBalanceEnough(long tokenId, long amount) {
		Map<Long, Long> assetMap = this.account.getAssetsMap();
		long currentAmount = assetMap.getOrDefault(tokenId, 0L);
		return amount > 0 && amount <= currentAmount;
	}

	/**
	 * reduce asset amount.
	 */
	public boolean reduceAssetAmount(long tokenId, long amount) {
		Map<Long, Long> assetMap = this.account.getAssetsMap();
		Long currentAmount = assetMap.get(tokenId);
		if (amount > 0 && null != currentAmount && amount <= currentAmount) {
			this.account = this.account.toBuilder()
				.putAssets(tokenId, Math.subtractExact(currentAmount, amount))
				.build();
			return true;
		}
		return false;
	}

	/**
	 * add asset amount.
	 */
	public boolean addAssetAmount(long tokenId, long amount) {
		Map<Long, Long> assetMap = this.account.getAssetsMap();
		Long currentAmount = assetMap.get(tokenId);
		if (currentAmount == null) {
			currentAmount = 0L;
		}
		this.account = this.account.toBuilder()
			.putAssets(tokenId, Math.addExact(currentAmount, amount))
			.build();
		return true;
	}

	public boolean addAsset(long tokenId, long value) {
		Map<Long, Long> assetMap = this.account.getAssetsMap();
		if (!assetMap.isEmpty() && assetMap.containsKey(tokenId)) {
			return false;
		}
		this.account = this.account.toBuilder()
			.putAssets(tokenId, value)
			.build();
		return true;
	}

	/**
	 * add asset.
	 */
	public boolean addAssetMap(Map<Long, Long> assetMap) {
		this.account = this.account.toBuilder().putAllAssets(assetMap).build();
		return true;
	}

	public Map<Long, Long> getAssetMap() {
		Map<Long, Long> assetMap = this.account.getAssetsMap();
		if (assetMap.isEmpty()) {
			assetMap = Maps.newHashMap();
		}

		return assetMap;
	}

	public long getLatestAssetOperationTime(long assetId) {
		return this.account.getAccountResource().getLatestAssetOperationTimeMap().getOrDefault(assetId, 0L);
	}

	public void putLatestAssetOperationTimeMap(Long key, Long value) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().putLatestAssetOperationTime(key, value).build();
		this.setAccountResource(newResource);
	}

	public long getFrozenBalanceForBandwidth() {
		return this.account.getFrozenForBandwidth().getFrozenBalance();
	}

	public long getAllFrozenBalanceForBandwidth() {
		return getFrozenBalanceForBandwidth() + getAcquiredDelegatedFrozenBalanceForBandwidth();
	}

	public int getFrozenSupplyCount() {
		return this.account.getFrozenAssetsCount();
	}

	public List<Account.Frozen> getFrozenSupplyList() {
		return getInstance().getFrozenAssetsList();
	}

	public long getFrozenSupplyBalance() {
		List<Account.Frozen> frozenSupplyList = getFrozenSupplyList();
		final long[] frozenSupplyBalance = {0};
		frozenSupplyList.forEach(frozen -> frozenSupplyBalance[0] = Long.sum(frozenSupplyBalance[0],
			frozen.getFrozenBalance()));
		return frozenSupplyBalance[0];
	}

	public Protocol.Stake getStake() {
		return getInstance().getStake();
	}

	public long getNormalStakeAmount() {
		if (this.account.hasStake())
			return this.account.getStake().getStakeAmount();
		return 0;
	}

	public long getTotalStakeAmount() {
		return getNormalStakeAmount() + (hasWitnessStake() ? getWitnessStake().getStakeAmount() : 0);
	}

	public void setStake(long stakeAmount, long expirationTime) {
		Protocol.Stake newStake = Protocol.Stake.newBuilder()
			.setStakeAmount(stakeAmount)
			.setExpirationTime(expirationTime)
			.build();
		this.account = this.account.toBuilder().setStake(newStake).build();
	}

	public boolean hasWitnessStake() {
		return this.account.hasWitnessStake();
	}

	public Stake getWitnessStake() {
		if (this.account.hasWitnessStake()) {
			return this.account.getWitnessStake();
		} else {
			return null;
		}
	}

	public void setWitnessStake(long stakeAmount) {
		Protocol.Stake newStake = Protocol.Stake.newBuilder()
			.setStakeAmount(stakeAmount)
			.setExpirationTime(-1)
			.build();
		this.account = this.account.toBuilder().setWitnessStake(newStake).build();
	}

	public long getWitnessStakeAmount() {
		if (hasWitnessStake())
			return getWitnessStake().getStakeAmount();
		return 0;
	}

	public long getStakeExpirationTime() {
		if (this.account.hasStake()) {
			return getStake().getExpirationTime();
		}
		return 0;
	}

	public void clearWitnessStake() {
		this.account = this.account.toBuilder().clearWitnessStake().build();
	}

	public long getAssetIssuedId() {
		return getInstance().getAssetIssuedId();
	}

	public void setAssetIssuedId(long id) {
		this.account = this.account.toBuilder().setAssetIssuedId(id).build();
	}

	public long getAllowance() {
		return getInstance().getAllowance();
	}

	public void setAllowance(long allowance) {
		this.account = this.account.toBuilder().setAllowance(allowance).build();
	}

	public long getLatestWithdrawTime() {
		return getInstance().getLatestWithdrawTime();
	}

	//for test only
	public void setLatestWithdrawTime(long latestWithdrawTime) {
		this.account = this.account.toBuilder()
			.setLatestWithdrawTime(latestWithdrawTime)
			.build();
	}

	public boolean getIsWitness() {
		return getInstance().getIsWitness();
	}

	public void setIsWitness(boolean isWitness) {
		this.account = this.account.toBuilder().setIsWitness(isWitness).build();
	}

	public boolean getIsCommittee() {
		return getInstance().getIsCommittee();
	}

	public void setIsCommittee(boolean isCommittee) {
		this.account = this.account.toBuilder().setIsCommittee(isCommittee).build();
	}

	public void setFrozenForBandwidth(long frozenBalance, long expireTime) {
		Account.Frozen newFrozen = Account.Frozen.newBuilder()
			.setFrozenBalance(frozenBalance)
			.setExpireTime(expireTime)
			.build();
		this.account = this.account.toBuilder().setFrozenForBandwidth(newFrozen).build();
	}

	public long getBandwidthUsage() {
		return this.account.getAccountResource().getBandwidthUsage();
	}

	public void setBandwidthUsage(long bandwidthUsage) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().setBandwidthUsage(bandwidthUsage).build();
		this.setAccountResource(newResource);
	}

	public AccountResource getAccountResource() {
		return this.account.getAccountResource();
	}

	public Account.Frozen getFrozenForEnergy() {
		return this.account.getFrozenForEnergy();
	}

	public Account.Frozen getFrozenForBandwidth() {
		return this.account.getFrozenForBandwidth();
	}

	public void setFrozenForEnergy(long newFrozenBalanceForEnergy, long time) {
		Account.Frozen newFrozen = Account.Frozen.newBuilder()
			.setFrozenBalance(newFrozenBalanceForEnergy)
			.setExpireTime(time)
			.build();
		this.account = this.account.toBuilder().setFrozenForEnergy(newFrozen).build();
	}

	public long getFrozenBalanceForEnergy() {
		return this.account.getFrozenForEnergy().getFrozenBalance();
	}

	public long getEnergyUsage() {
		return this.account.getAccountResource().getEnergyUsage();
	}

	public void setEnergyUsage(long energyUsage) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().setEnergyUsage(energyUsage).build();
		this.setAccountResource(newResource);
	}

	public long getAllFrozenBalanceForEnergy() {
		return getFrozenBalanceForEnergy() + getAcquiredDelegatedFrozenBalanceForEnergy();
	}

	public long getLatestEnergyConsumeTime() {
		return this.account.getAccountResource().getLatestEnergyConsumeTime();
	}

	public void setLatestEnergyConsumeTime(long time) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().setLatestEnergyConsumeTime(time).build();
		this.setAccountResource(newResource);
	}

	public long getFreeBandwidthUsage() {
		return this.account.getAccountResource().getFreeBandwidthUsage();
	}

	public void setFreeBandwidthUsage(long freeBandwidthUsage) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().setFreeBandwidthUsage(freeBandwidthUsage).build();
		this.setAccountResource(newResource);
	}

	public long getFreeAssetBandwidthUsage(Long assetId) {
		return this.getAccountResource().getAssetFreeBandwidthUsageMap().getOrDefault(assetId, 0L);
	}

	public Map<Long, Long> getAllFreeAssetBandwidthUsage() {
		return this.account.getAccountResource().getAssetFreeBandwidthUsageMap();
	}

	public void putFreeAssetBandwidthUsage(Long id, long freeAssetBandwidthUsage) {
		Account.AccountResource newResource = this.account.getAccountResource()
			.toBuilder().putAssetFreeBandwidthUsage(id, freeAssetBandwidthUsage).build();
		this.setAccountResource(newResource);
	}

	public Permission getPermissionById(int id) {
		if (id == 0) {
			if (this.account.hasOwnerPermission()) {
				return this.account.getOwnerPermission();
			}
			return getDefaultPermission(this.account.getAddress());
		}
		if (id == 1) {
			if (this.account.hasWitnessPermission()) {
				return this.account.getWitnessPermission();
			}
			return null;
		}
		for (Permission permission : this.account.getActivePermissionList()) {
			if (id == permission.getId()) {
				return permission;
			}
		}
		return null;
	}

	public void updatePermissions(Permission owner, Permission witness, List<Permission> actives) {
		Account.Builder builder = this.account.toBuilder();
		owner = owner.toBuilder().setId(0).build();
		builder.setOwnerPermission(owner);
		if (builder.getIsWitness()) {
			witness = witness.toBuilder().setId(1).build();
			builder.setWitnessPermission(witness);
		}
		builder.clearActivePermission();
		for (int i = 0; i < actives.size(); i++) {
			Permission permission = actives.get(i).toBuilder().setId(i + 2).build();
			builder.addActivePermission(permission);
		}
		this.account = builder.build();
	}

}