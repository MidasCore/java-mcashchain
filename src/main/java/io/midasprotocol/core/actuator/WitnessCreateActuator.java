package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.VoteChangeCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.capsule.utils.TransactionUtil;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.db.AccountStore;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.db.VoteChangeStore;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.StakeUtil;
import io.midasprotocol.protos.Contract.WitnessCreateContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j(topic = "actuator")
public class WitnessCreateActuator extends AbstractActuator {

	WitnessCreateActuator(final Any contract, final Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final WitnessCreateContract witnessCreateContract = this.contract
				.unpack(WitnessCreateContract.class);
			this.createWitness(witnessCreateContract);
			this.updateVote(witnessCreateContract);
			ret.setStatus(fee, Code.SUCCESS);
		} catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
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
		if (!this.contract.is(WitnessCreateContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected WitnessCreateContract, actual" + contract.getClass());
		}
		final WitnessCreateContract contract;
		try {
			contract = this.contract.unpack(WitnessCreateContract.class);
		} catch (InvalidProtocolBufferException e) {
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}

		byte[] supernodeAddress = contract.getWitnessAddress().toByteArray();
		if (!Wallet.addressValid(supernodeAddress)) {
			throw new ContractValidateException("Invalid supernodeAddress");
		}

		if (!TransactionUtil.validUrl(contract.getUrl().toByteArray())) {
			throw new ContractValidateException("Invalid url");
		}

		AccountCapsule ownerAccountCapsule = this.dbManager.getAccountStore().get(ownerAddress);

		if (ownerAccountCapsule == null) {
			throw new ContractValidateException("Account " + readableOwnerAddress + " does not exist");
		}

		AccountCapsule nodeAccountCapsule = this.dbManager.getAccountStore().get(supernodeAddress);
		if (nodeAccountCapsule == null) {
			throw new ContractValidateException("Account " + StringUtil.createReadableString(supernodeAddress)
				+ " does not exist");
		}

		if (this.dbManager.getWitnessStore().has(supernodeAddress)) {
			throw new ContractValidateException("Witness " + StringUtil.createReadableString(supernodeAddress)
				+ " has existed");
		}

		if (ownerAccountCapsule.hasWitnessStake()) {
			throw new ContractValidateException("Account " + StringUtil.createReadableString(ownerAddress)
				+ " owns other supernode");
		}

		if (ownerAccountCapsule.getBalance() < dbManager.getDynamicPropertiesStore().getAccountUpgradeCost()) {
			throw new ContractValidateException("balance < AccountUpgradeCost");
		}

		if (ownerAccountCapsule.getNormalStakeAmount() < Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT) {
			throw new ContractValidateException(String.format("Owner stake amount %d < required stake amount %d",
				ownerAccountCapsule.getNormalStakeAmount() / Parameter.ChainConstant.TEN_POW_DECIMALS,
				Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT / Parameter.ChainConstant.TEN_POW_DECIMALS));
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(WitnessCreateContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return dbManager.getDynamicPropertiesStore().getAccountUpgradeCost();
	}

	// Create Witness by witnessCreateContract
	private void createWitness(final WitnessCreateContract witnessCreateContract)
		throws BalanceInsufficientException {
		final WitnessCapsule witnessCapsule = new WitnessCapsule(
			witnessCreateContract.getWitnessAddress(),
			witnessCreateContract.getOwnerAddress(),
			0,
			witnessCreateContract.getUrl().toStringUtf8());

		logger.debug("Create supernode, address {}, ownerAddress: {}",
			StringUtil.createReadableString(witnessCapsule.getAddress()),
			StringUtil.createReadableString(witnessCapsule.getOwnerAddress()));
		this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);
		AccountCapsule witnessAccountCapsule = this.dbManager.getAccountStore()
			.get(witnessCapsule.createDbKey());
		witnessAccountCapsule.setIsWitness(true);
		if (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1) {
			witnessAccountCapsule.setDefaultWitnessPermission(dbManager);
		}
		this.dbManager.getAccountStore().put(witnessAccountCapsule.createDbKey(), witnessAccountCapsule);

		AccountCapsule ownerAccountCapsule = this.dbManager.getAccountStore()
			.get(witnessCreateContract.getOwnerAddress().toByteArray());

		long newStake = ownerAccountCapsule.getNormalStakeAmount() - Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT;
		long now = dbManager.getHeadBlockTimeStamp();
		long duration = dbManager.getDynamicPropertiesStore().getStakeTimeInDay() * Parameter.TimeConstant.MS_PER_DAY;

		ownerAccountCapsule.setStake(newStake, now + duration);
		ownerAccountCapsule.setWitnessStake(Parameter.NodeConstant.SUPER_NODE_STAKE_AMOUNT);

		this.dbManager.getAccountStore().put(ownerAccountCapsule.createDbKey(), ownerAccountCapsule);
		long cost = dbManager.getDynamicPropertiesStore().getAccountUpgradeCost();

		dbManager.adjustBalance(witnessCreateContract.getOwnerAddress().toByteArray(), -cost);
		dbManager.adjustBalance(this.dbManager.getAccountStore().getBlackhole().createDbKey(), +cost);

		dbManager.getDynamicPropertiesStore().addTotalCreateWitnessCost(cost);
	}

	// clear old vote & vote for own supernode
	private void updateVote(final WitnessCreateContract witnessCreateContract) {
		byte[] ownerAddress = witnessCreateContract.getOwnerAddress().toByteArray();

		VoteChangeCapsule voteChangeCapsule;
		VoteChangeStore voteChangeStore = dbManager.getVoteChangeStore();
		AccountStore accountStore = dbManager.getAccountStore();

		AccountCapsule accountCapsule = (Objects.isNull(getDeposit())) ? accountStore.get(ownerAddress)
			: getDeposit().getAccount(ownerAddress);

		if (!Objects.isNull(getDeposit())) {
			VoteChangeCapsule vCapsule = getDeposit().getVoteChangeCapsule(ownerAddress);
			if (Objects.isNull(vCapsule)) {
				voteChangeCapsule = new VoteChangeCapsule(witnessCreateContract.getOwnerAddress(),
					accountCapsule.getVote());
			} else {
				voteChangeCapsule = vCapsule;
			}
		} else if (!voteChangeStore.has(ownerAddress)) {
			voteChangeCapsule = new VoteChangeCapsule(witnessCreateContract.getOwnerAddress(),
				accountCapsule.getVote());
		} else {
			voteChangeCapsule = voteChangeStore.get(ownerAddress);
		}

		accountCapsule.clearVote();
		voteChangeCapsule.clearNewVote();

		ByteString supernodeAddress = witnessCreateContract.getWitnessAddress();
		long voteCount = StakeUtil.getVotingPowerFromStakeAmount(accountCapsule.getTotalStakeAmount());

		logger.info("address: {}, supernode: {}, voteCount: {}",
			StringUtil.createReadableString(ownerAddress),
			StringUtil.createReadableString(supernodeAddress),
			voteCount);

		voteChangeCapsule.setNewVote(supernodeAddress, voteCount);
		accountCapsule.setVote(supernodeAddress, voteCount);

		if (Objects.isNull(deposit)) {
			accountStore.put(accountCapsule.createDbKey(), accountCapsule);
			voteChangeStore.put(ownerAddress, voteChangeCapsule);
		} else {
			// cache
			deposit.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
			deposit.putVoteChangeValue(ownerAddress, voteChangeCapsule);
		}
	}
}
