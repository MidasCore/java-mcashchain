package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.capsule.utils.TransactionUtil;
import org.tron.core.config.Parameter;
import org.tron.core.db.Manager;
import org.tron.core.exception.BalanceInsufficientException;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.WitnessCreateContract;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.util.List;

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
			ret.setStatus(fee, code.SUCCESS);
		} catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		return true;
	}

	// TODO: nghiand check stake amount, add owner address
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

		byte[] supernodeAddress = contract.getSupernodeAddress().toByteArray();
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

		if (ownerAccountCapsule.getBalance() < dbManager.getDynamicPropertiesStore().getAccountUpgradeCost()) {
			logger.info("" + ownerAccountCapsule.getBalance() + " " + dbManager.getDynamicPropertiesStore().getAccountUpgradeCost());
			throw new ContractValidateException("balance < AccountUpgradeCost");
		}

		int witnessCount = 0;
		List<WitnessCapsule> witnesses = dbManager.getWitnessStore().getAllWitnesses();
		for (WitnessCapsule witness : witnesses) {
			if (witness.getOwnerAddress().equals(contract.getOwnerAddress())) {
				witnessCount++;
			}
		}

		if (ownerAccountCapsule.getStakeAmount() < Parameter.NodeConstant.MASTER_NODE_STAKE_AMOUNT * (witnessCount + 1)) {
			throw new ContractValidateException("Owner stake amount < required stake amount "
					+ Parameter.NodeConstant.MASTER_NODE_STAKE_AMOUNT / Parameter.ChainConstant.TEN_POW_DECIMALS
					+ " MCASH");
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

	private void createWitness(final WitnessCreateContract witnessCreateContract)
			throws BalanceInsufficientException {
		//Create Witness by witnessCreateContract
		final WitnessCapsule witnessCapsule = new WitnessCapsule(
				witnessCreateContract.getSupernodeAddress(),
				witnessCreateContract.getOwnerAddress(),
				0,
				witnessCreateContract.getUrl().toStringUtf8());

		logger.debug("Create supernode, address {}, ownerAddress: {}",
				StringUtil.createReadableString(witnessCapsule.getAddress()),
				StringUtil.createReadableString(witnessCapsule.getOwnerAddress()));
		this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);
		AccountCapsule accountCapsule = this.dbManager.getAccountStore()
				.get(witnessCapsule.createDbKey());
		accountCapsule.setIsWitness(true);
		if (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1) {
			accountCapsule.setDefaultWitnessPermission(dbManager);
		}
		this.dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		long cost = dbManager.getDynamicPropertiesStore().getAccountUpgradeCost();
		dbManager.adjustBalance(witnessCreateContract.getOwnerAddress().toByteArray(), -cost);

		dbManager.adjustBalance(this.dbManager.getAccountStore().getBlackhole().createDbKey(), +cost);

		dbManager.getDynamicPropertiesStore().addTotalCreateWitnessCost(cost);
	}
}
