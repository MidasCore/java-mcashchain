package io.midasprotocol.core.actuator;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.UnfreezeAssetContract;
import io.midasprotocol.protos.Protocol.Account.Frozen;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;

@Slf4j(topic = "actuator")
public class UnfreezeAssetActuator extends AbstractActuator {

	UnfreezeAssetActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final UnfreezeAssetContract unfreezeAssetContract = contract
				.unpack(UnfreezeAssetContract.class);
			byte[] ownerAddress = unfreezeAssetContract.getOwnerAddress().toByteArray();

			AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			long unfreezeAsset = 0L;
			List<Frozen> frozenList = Lists.newArrayList();
			frozenList.addAll(accountCapsule.getFrozenSupplyList());
			Iterator<Frozen> iterator = frozenList.iterator();
			long now = dbManager.getHeadBlockTimeStamp();
			while (iterator.hasNext()) {
				Frozen next = iterator.next();
				if (next.getExpireTime() <= now) {
					unfreezeAsset += next.getFrozenBalance();
					iterator.remove();
				}
			}

			accountCapsule.addAssetAmountV2(accountCapsule.getAssetIssuedId(), unfreezeAsset);
			accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
				.clearFrozenSupply().addAllFrozenSupply(frozenList).build());

			dbManager.getAccountStore().put(ownerAddress, accountCapsule);
			ret.setStatus(fee, code.SUCCESS);
		} catch (InvalidProtocolBufferException | ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
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
		if (!this.contract.is(UnfreezeAssetContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected UnfreezeAssetContract, actual" + contract.getClass());
		}
		final UnfreezeAssetContract unfreezeAssetContract;
		try {
			unfreezeAssetContract = this.contract.unpack(UnfreezeAssetContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = unfreezeAssetContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
				"Account " + readableOwnerAddress + " does not exist");
		}

		if (accountCapsule.getFrozenSupplyCount() <= 0) {
			throw new ContractValidateException("No frozen supply balance");
		}

		if (accountCapsule.getAssetIssuedId() == 0) {
			throw new ContractValidateException("This account did not issue any asset");
		}

		long now = dbManager.getHeadBlockTimeStamp();
		long allowedUnfreezeCount = accountCapsule.getFrozenSupplyList().stream()
			.filter(frozen -> frozen.getExpireTime() <= now).count();
		if (allowedUnfreezeCount <= 0) {
			throw new ContractValidateException("It's not time to unfreeze asset supply");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(UnfreezeAssetContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

}
