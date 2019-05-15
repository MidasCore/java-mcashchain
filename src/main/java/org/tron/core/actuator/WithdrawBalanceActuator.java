package org.tron.core.actuator;

import com.google.common.math.LongMath;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.Parameter;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.WithdrawBalanceContract;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

import static org.tron.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.tron.core.actuator.ActuatorConstant.NOT_EXIST_STR;

@Slf4j(topic = "actuator")
public class WithdrawBalanceActuator extends AbstractActuator {

	WithdrawBalanceActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		final WithdrawBalanceContract withdrawBalanceContract;
		try {
			withdrawBalanceContract = contract.unpack(WithdrawBalanceContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		}

		AccountCapsule accountCapsule = (Objects.isNull(getDeposit())) ? dbManager.getAccountStore().
				get(withdrawBalanceContract.getOwnerAddress().toByteArray())
				: getDeposit().getAccount(withdrawBalanceContract.getOwnerAddress().toByteArray());
		long oldBalance = accountCapsule.getBalance();
		long allowance = accountCapsule.getAllowance();

		long now = dbManager.getHeadBlockTimeStamp();
		accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
				.setBalance(oldBalance + allowance)
				.setAllowance(0L)
				.setLatestWithdrawTime(now)
				.build());
		if (Objects.isNull(getDeposit())) {
			dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		} else {
			// cache
			deposit.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
		}

		ret.setWithdrawAmount(allowance);
		ret.setStatus(fee, code.SUCCESS);

		return true;
	}

	@Override
	public boolean validate() throws ContractValidateException {
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (dbManager == null && (getDeposit() == null || getDeposit().getDbManager() == null)) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!this.contract.is(WithdrawBalanceContract.class)) {
			throw new ContractValidateException(
					"Contract type error, expected WithdrawBalanceContract, actual " + contract.getClass());
		}
		final WithdrawBalanceContract withdrawBalanceContract;
		try {
			withdrawBalanceContract = this.contract.unpack(WithdrawBalanceContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = withdrawBalanceContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule =
				Objects.isNull(getDeposit()) ? dbManager.getAccountStore().get(ownerAddress)
						: getDeposit().getAccount(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
					ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
		}

		String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
		if (!dbManager.getStakeAccountStore().has(ownerAddress)) {
			throw new ContractValidateException(
					ACCOUNT_EXCEPTION_STR + readableOwnerAddress + " is not a stakeAccount");
		}

		boolean isGP = Args.getInstance().getGenesisBlock().getWitnesses().stream().anyMatch(witness ->
				Arrays.equals(ownerAddress, witness.getAddress()));
		if (isGP) {
			throw new ContractValidateException(
					ACCOUNT_EXCEPTION_STR + readableOwnerAddress
							+ " is a guard representative and is not allowed to withdraw Balance");
		}

		long latestWithdrawTime = accountCapsule.getLatestWithdrawTime();
		long now = dbManager.getHeadBlockTimeStamp();
		long witnessAllowanceFrozenTime = Objects.isNull(getDeposit()) ?
				dbManager.getDynamicPropertiesStore().getWitnessAllowanceFrozenTime() * Parameter.TimeConstant.MS_PER_DAY :
				getDeposit().getWitnessAllowanceFrozenTime() * Parameter.TimeConstant.MS_PER_DAY;

		if (now - latestWithdrawTime < witnessAllowanceFrozenTime) {
			throw new ContractValidateException("The last withdraw time is "
					+ latestWithdrawTime + ", less than 24 hours");
		}

		if (accountCapsule.getAllowance() <= 0) {
			throw new ContractValidateException("witnessAccount does not have any allowance");
		}
		try {
			LongMath.checkedAdd(accountCapsule.getBalance(), accountCapsule.getAllowance());
		} catch (ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(WithdrawBalanceContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
