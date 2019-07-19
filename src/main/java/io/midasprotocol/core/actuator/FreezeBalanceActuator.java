package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.DelegatedResourceAccountIndexCapsule;
import io.midasprotocol.core.capsule.DelegatedResourceCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.util.ConversionUtil;
import io.midasprotocol.protos.Contract.FreezeBalanceContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j(topic = "actuator")
public class FreezeBalanceActuator extends AbstractActuator {

	FreezeBalanceActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		final FreezeBalanceContract freezeBalanceContract;
		try {
			freezeBalanceContract = contract.unpack(FreezeBalanceContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore()
			.get(freezeBalanceContract.getOwnerAddress().toByteArray());

		long now = dbManager.getHeadBlockTimeStamp();
		long duration = freezeBalanceContract.getFrozenDuration() * Parameter.TimeConstant.MS_PER_DAY;

		long newBalance = accountCapsule.getBalance() - freezeBalanceContract.getFrozenBalance();

		long frozenBalance = freezeBalanceContract.getFrozenBalance();
		long expireTime = now + duration;
		byte[] ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
		byte[] receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();

		switch (freezeBalanceContract.getResource()) {
			case BANDWIDTH:
				if (!ArrayUtils.isEmpty(receiverAddress)
					&& dbManager.getDynamicPropertiesStore().supportDR()) {
					delegateResource(ownerAddress, receiverAddress, true,
						frozenBalance, expireTime);
					accountCapsule.addDelegatedFrozenBalanceForBandwidth(frozenBalance);
				} else {
					long newFrozenBalanceForBandwidth =
						frozenBalance + accountCapsule.getFrozenBalanceForBandwidth();
					accountCapsule.setFrozenForBandwidth(newFrozenBalanceForBandwidth, expireTime);
				}
				dbManager.getDynamicPropertiesStore()
					.addTotalBandwidthWeight(frozenBalance / Parameter.ChainConstant.TEN_POW_DECIMALS);
				break;
			case ENERGY:
				if (!ArrayUtils.isEmpty(receiverAddress)
					&& dbManager.getDynamicPropertiesStore().supportDR()) {
					delegateResource(ownerAddress, receiverAddress, false,
						frozenBalance, expireTime);
					accountCapsule.addDelegatedFrozenBalanceForEnergy(frozenBalance);
				} else {
					long newFrozenBalanceForEnergy =
						frozenBalance + accountCapsule.getFrozenBalanceForEnergy();
					accountCapsule.setFrozenForEnergy(newFrozenBalanceForEnergy, expireTime);
				}
				dbManager.getDynamicPropertiesStore()
					.addTotalEnergyWeight(frozenBalance / Parameter.ChainConstant.TEN_POW_DECIMALS);
				break;
		}

		accountCapsule.setBalance(newBalance);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		ret.setStatus(fee, Code.SUCCESS);

		return true;
	}


	@Override
	public boolean 	validate() throws ContractValidateException {
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (this.dbManager == null) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!contract.is(FreezeBalanceContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected FreezeBalanceContract, actual" + contract.getClass());
		}

		final FreezeBalanceContract freezeBalanceContract;
		try {
			freezeBalanceContract = this.contract.unpack(FreezeBalanceContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
				"Account " + readableOwnerAddress + " does not exist");
		}

		long frozenBalance = freezeBalanceContract.getFrozenBalance();
		if (frozenBalance <= 0) {
			throw new ContractValidateException("frozenBalance must be positive");
		}
		if (frozenBalance < ConversionUtil.McashToMatoshi(1)) {
			throw new ContractValidateException("frozenBalance must be more than 1 MCASH");
		}

		if (frozenBalance > accountCapsule.getBalance()) {
			throw new ContractValidateException("frozenBalance must be less than accountBalance");
		}

//    long maxFrozenNumber = dbManager.getDynamicPropertiesStore().getMaxFrozenNumber();
//    if (accountCapsule.getFrozenCount() >= maxFrozenNumber) {
//      throw new ContractValidateException("max frozen number is: " + maxFrozenNumber);
//    }

		long frozenDuration = freezeBalanceContract.getFrozenDuration();
		long minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
		long maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();

		boolean needCheckFrozenTime = Args.getInstance().getCheckFrozenTime() == 1;//for test
		if (needCheckFrozenTime && !(frozenDuration >= minFrozenTime
			&& frozenDuration <= maxFrozenTime)) {
			throw new ContractValidateException(
				"frozenDuration must be less than " + maxFrozenTime + " days "
					+ "and more than " + minFrozenTime + " days");
		}

		switch (freezeBalanceContract.getResource()) {
			case BANDWIDTH:
			case ENERGY:
				break;
			default:
				throw new ContractValidateException(
					"ResourceCode error, valid ResourceCode [BANDWIDTH、ENERGY]");
		}

		//todo：need version control and config for delegating resource
		byte[] receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();
		//If the receiver is included in the contract, the receiver will receive the resource.
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
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(FreezeBalanceContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

	private void delegateResource(byte[] ownerAddress, byte[] receiverAddress, boolean isBandwidth,
								  long balance, long expireTime) {
		byte[] key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);
		//modify DelegatedResourceStore
		DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
			.get(key);
		if (delegatedResourceCapsule != null) {
			if (isBandwidth) {
				delegatedResourceCapsule.addFrozenBalanceForBandwidth(balance, expireTime);
			} else {
				delegatedResourceCapsule.addFrozenBalanceForEnergy(balance, expireTime);
			}
		} else {
			delegatedResourceCapsule = new DelegatedResourceCapsule(
				ByteString.copyFrom(ownerAddress),
				ByteString.copyFrom(receiverAddress));
			if (isBandwidth) {
				delegatedResourceCapsule.setFrozenBalanceForBandwidth(balance, expireTime);
			} else {
				delegatedResourceCapsule.setFrozenBalanceForEnergy(balance, expireTime);
			}

		}
		dbManager.getDelegatedResourceStore().put(key, delegatedResourceCapsule);

		//modify DelegatedResourceAccountIndexStore
		{
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager
				.getDelegatedResourceAccountIndexStore()
				.get(ownerAddress);
			if (delegatedResourceAccountIndexCapsule == null) {
				delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(
					ByteString.copyFrom(ownerAddress));
			}
			List<ByteString> toAccountsList = delegatedResourceAccountIndexCapsule.getToAccountsList();
			if (!toAccountsList.contains(ByteString.copyFrom(receiverAddress))) {
				delegatedResourceAccountIndexCapsule.addToAccount(ByteString.copyFrom(receiverAddress));
			}
			dbManager.getDelegatedResourceAccountIndexStore()
				.put(ownerAddress, delegatedResourceAccountIndexCapsule);
		}

		{
			DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager
				.getDelegatedResourceAccountIndexStore()
				.get(receiverAddress);
			if (delegatedResourceAccountIndexCapsule == null) {
				delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(
					ByteString.copyFrom(receiverAddress));
			}
			List<ByteString> fromAccountsList = delegatedResourceAccountIndexCapsule
				.getFromAccountsList();
			if (!fromAccountsList.contains(ByteString.copyFrom(ownerAddress))) {
				delegatedResourceAccountIndexCapsule.addFromAccount(ByteString.copyFrom(ownerAddress));
			}
			dbManager.getDelegatedResourceAccountIndexStore()
				.put(receiverAddress, delegatedResourceAccountIndexCapsule);
		}

		//modify AccountStore
		AccountCapsule receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
		if (isBandwidth) {
			receiverCapsule.addAcquiredDelegatedFrozenBalanceForBandwidth(balance);
		} else {
			receiverCapsule.addAcquiredDelegatedFrozenBalanceForEnergy(balance);
		}

		dbManager.getAccountStore().put(receiverCapsule.createDbKey(), receiverCapsule);
	}

}
