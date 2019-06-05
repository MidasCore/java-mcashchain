package io.midasprotocol.core.actuator;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.*;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.UnfreezeBalanceContract;
import io.midasprotocol.protos.Protocol.Account.AccountResource;
import io.midasprotocol.protos.Protocol.Account.Frozen;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Slf4j(topic = "actuator")
public class UnfreezeBalanceActuator extends AbstractActuator {

	UnfreezeBalanceActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		final UnfreezeBalanceContract unfreezeBalanceContract;
		try {
			unfreezeBalanceContract = contract.unpack(UnfreezeBalanceContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		byte[] ownerAddress = unfreezeBalanceContract.getOwnerAddress().toByteArray();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		long oldBalance = accountCapsule.getBalance();

		long unfreezeBalance = 0L;

		byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
		//If the receiver is not included in the contract, unfreeze frozen balance for this account.
		//otherwise,unfreeze delegated frozen balance provided this account.
		if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
			byte[] key = DelegatedResourceCapsule
				.createDbKey(unfreezeBalanceContract.getOwnerAddress().toByteArray(),
					unfreezeBalanceContract.getReceiverAddress().toByteArray());
			DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore().get(key);

			AccountCapsule receiverCapsule = dbManager.getAccountStore().get(receiverAddress);

			switch (unfreezeBalanceContract.getResource()) {
				case BANDWIDTH:
					unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForBandwidth();
					delegatedResourceCapsule.setFrozenBalanceForBandwidth(0, 0);
					receiverCapsule.addAcquiredDelegatedFrozenBalanceForBandwidth(-unfreezeBalance);
					accountCapsule.addDelegatedFrozenBalanceForBandwidth(-unfreezeBalance);
					break;
				case ENERGY:
					unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForEnergy();
					delegatedResourceCapsule.setFrozenBalanceForEnergy(0, 0);
					receiverCapsule.addAcquiredDelegatedFrozenBalanceForEnergy(-unfreezeBalance);
					accountCapsule.addDelegatedFrozenBalanceForEnergy(-unfreezeBalance);
					break;
				default:
					//this should never happen
					break;
			}
			accountCapsule.setBalance(oldBalance + unfreezeBalance);

			dbManager.getAccountStore().put(receiverCapsule.createDbKey(), receiverCapsule);

			if (delegatedResourceCapsule.getFrozenBalanceForBandwidth() == 0
				&& delegatedResourceCapsule.getFrozenBalanceForEnergy() == 0) {
				dbManager.getDelegatedResourceStore().delete(key);

				//modify DelegatedResourceAccountIndexStore
				{
					DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager
						.getDelegatedResourceAccountIndexStore()
						.get(ownerAddress);
					if (delegatedResourceAccountIndexCapsule != null) {
						List<ByteString> toAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
							.getToAccountsList());
						toAccountsList.remove(ByteString.copyFrom(receiverAddress));
						delegatedResourceAccountIndexCapsule.setAllToAccounts(toAccountsList);
						dbManager.getDelegatedResourceAccountIndexStore()
							.put(ownerAddress, delegatedResourceAccountIndexCapsule);
					}
				}

				{
					DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager
						.getDelegatedResourceAccountIndexStore()
						.get(receiverAddress);
					if (delegatedResourceAccountIndexCapsule != null) {
						List<ByteString> fromAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
							.getFromAccountsList());
						fromAccountsList.remove(ByteString.copyFrom(ownerAddress));
						delegatedResourceAccountIndexCapsule.setAllFromAccounts(fromAccountsList);
						dbManager.getDelegatedResourceAccountIndexStore()
							.put(receiverAddress, delegatedResourceAccountIndexCapsule);
					}
				}

			} else {
				dbManager.getDelegatedResourceStore().put(key, delegatedResourceCapsule);
			}
		} else {
			switch (unfreezeBalanceContract.getResource()) {
				case BANDWIDTH:

					List<Frozen> frozenList = Lists.newArrayList();
					frozenList.addAll(accountCapsule.getFrozenList());
					Iterator<Frozen> iterator = frozenList.iterator();
					long now = dbManager.getHeadBlockTimeStamp();
					while (iterator.hasNext()) {
						Frozen next = iterator.next();
						if (next.getExpireTime() <= now) {
							unfreezeBalance += next.getFrozenBalance();
							iterator.remove();
						}
					}

					accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
						.setBalance(oldBalance + unfreezeBalance)
						.clearFrozen().addAllFrozen(frozenList).build());

					break;
				case ENERGY:
					unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForEnergy()
						.getFrozenBalance();

					AccountResource newAccountResource = accountCapsule.getAccountResource().toBuilder()
						.clearFrozenBalanceForEnergy().build();
					accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
						.setBalance(oldBalance + unfreezeBalance)
						.setAccountResource(newAccountResource).build());

					break;
				default:
					//this should never happen
					break;
			}

		}

		switch (unfreezeBalanceContract.getResource()) {
			case BANDWIDTH:
				dbManager.getDynamicPropertiesStore()
					.addTotalNetWeight(-unfreezeBalance / Parameter.ChainConstant.TEN_POW_DECIMALS);
				break;
			case ENERGY:
				dbManager.getDynamicPropertiesStore()
					.addTotalEnergyWeight(-unfreezeBalance / Parameter.ChainConstant.TEN_POW_DECIMALS);
				break;
			default:
				//this should never happen
				break;
		}

		VoteChangeCapsule voteChangeCapsule;
		if (!dbManager.getVoteChangeStore().has(ownerAddress)) {
			voteChangeCapsule = new VoteChangeCapsule(unfreezeBalanceContract.getOwnerAddress(),
				accountCapsule.getVote());
		} else {
			voteChangeCapsule = dbManager.getVoteChangeStore().get(ownerAddress);
		}
		accountCapsule.clearVote();
		voteChangeCapsule.clearNewVote();

		dbManager.getAccountStore().put(ownerAddress, accountCapsule);

		dbManager.getVoteChangeStore().put(ownerAddress, voteChangeCapsule);

		ret.setUnfreezeAmount(unfreezeBalance);
		ret.setStatus(fee, code.SUCCESS);

		return true;
	}

	@Override
	public boolean validate() throws ContractValidateException {
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (this.dbManager == null) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(UnfreezeBalanceContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected UnfreezeBalanceContract, actual " + contract.getClass());
		}
		final UnfreezeBalanceContract unfreezeBalanceContract;
		try {
			unfreezeBalanceContract = this.contract.unpack(UnfreezeBalanceContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = unfreezeBalanceContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
				"Account " + readableOwnerAddress + " does not exist");
		}
		long now = dbManager.getHeadBlockTimeStamp();
		byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
		//If the receiver is not included in the contract, unfreeze frozen balance for this account.
		//otherwise,unfreeze delegated frozen balance provided this account.
		if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
			if (Arrays.equals(receiverAddress, ownerAddress)) {
				throw new ContractValidateException(
					"receiverAddress must not be the same as ownerAddress");
			}

			if (!Wallet.addressValid(receiverAddress)) {
				throw new ContractValidateException("Invalid receiverAddress");
			}

			AccountCapsule receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
			if (receiverCapsule == null) {
				String readableOwnerAddress = StringUtil.createReadableString(receiverAddress);
				throw new ContractValidateException(
					"Account " + readableOwnerAddress + " does not exist");
			}

			byte[] key = DelegatedResourceCapsule
				.createDbKey(unfreezeBalanceContract.getOwnerAddress().toByteArray(),
					unfreezeBalanceContract.getReceiverAddress().toByteArray());
			DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
				.get(key);
			if (delegatedResourceCapsule == null) {
				throw new ContractValidateException(
					"Delegated resource doest not exist");
			}

			switch (unfreezeBalanceContract.getResource()) {
				case BANDWIDTH:
					if (delegatedResourceCapsule.getFrozenBalanceForBandwidth() <= 0) {
						throw new ContractValidateException("No delegatedFrozenBalance (Bandwidth)");
					}
					if (receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth()
						< delegatedResourceCapsule.getFrozenBalanceForBandwidth()) {
						throw new ContractValidateException(
							"AcquiredDelegatedFrozenBalanceForBandwidth " + receiverCapsule
								.getAcquiredDelegatedFrozenBalanceForBandwidth() + " < delegatedBandwidth "
								+ delegatedResourceCapsule.getFrozenBalanceForBandwidth()
								+ ", this should never happen");
					}
					if (delegatedResourceCapsule.getExpireTimeForBandwidth() > now) {
						throw new ContractValidateException("It's not time to unfreeze.");
					}
					break;
				case ENERGY:
					if (delegatedResourceCapsule.getFrozenBalanceForEnergy() <= 0) {
						throw new ContractValidateException("No delegateFrozenBalance (Energy)");
					}
					if (receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy()
						< delegatedResourceCapsule.getFrozenBalanceForEnergy()) {
						throw new ContractValidateException(
							"AcquiredDelegatedFrozenBalanceForEnergy " + receiverCapsule
								.getAcquiredDelegatedFrozenBalanceForEnergy() + " < delegatedEnergy"
								+ delegatedResourceCapsule.getFrozenBalanceForEnergy() +
								", this should never happen");
					}
					if (delegatedResourceCapsule.getExpireTimeForEnergy(dbManager) > now) {
						throw new ContractValidateException("It's not time to unfreeze.");
					}
					break;
				default:
					throw new ContractValidateException(
						"ResourceCode error.valid ResourceCode (Bandwidth、Energy)");
			}

		} else {
			switch (unfreezeBalanceContract.getResource()) {
				case BANDWIDTH:
					if (accountCapsule.getFrozenCount() <= 0) {
						throw new ContractValidateException("No frozenBalance (Bandwidth)");
					}

					long allowedUnfreezeCount = accountCapsule.getFrozenList().stream()
						.filter(frozen -> frozen.getExpireTime() <= now).count();
					if (allowedUnfreezeCount <= 0) {
						throw new ContractValidateException("It's not time to unfreeze (Bandwidth).");
					}
					break;
				case ENERGY:
					Frozen frozenBalanceForEnergy = accountCapsule.getAccountResource()
						.getFrozenBalanceForEnergy();
					if (frozenBalanceForEnergy.getFrozenBalance() <= 0) {
						throw new ContractValidateException("No frozenBalance (Energy)");
					}
					if (frozenBalanceForEnergy.getExpireTime() > now) {
						throw new ContractValidateException("It's not time to unfreeze (Energy).");
					}

					break;
				default:
					throw new ContractValidateException(
						"ResourceCode error.valid ResourceCode [Bandwidth、Energy]");
			}

		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(UnfreezeBalanceContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
