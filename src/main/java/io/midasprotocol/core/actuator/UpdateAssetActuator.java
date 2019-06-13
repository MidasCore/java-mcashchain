package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.utils.TransactionUtil;
import io.midasprotocol.core.db.AssetIssueStore;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.AccountUpdateContract;
import io.midasprotocol.protos.Contract.UpdateAssetContract;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "actuator")
public class UpdateAssetActuator extends AbstractActuator {

	UpdateAssetActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final UpdateAssetContract updateAssetContract = this.contract
				.unpack(UpdateAssetContract.class);

			long newLimit = updateAssetContract.getNewLimit();
			long newPublicLimit = updateAssetContract.getNewPublicLimit();
			byte[] ownerAddress = updateAssetContract.getOwnerAddress().toByteArray();
			ByteString newUrl = updateAssetContract.getUrl();
			ByteString newDescription = updateAssetContract.getDescription();

			AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);

			AssetIssueCapsule assetIssueCapsule, assetIssueCapsuleV2;

			AssetIssueStore assetIssueStore = dbManager.getAssetIssueStore();
			assetIssueCapsuleV2 = assetIssueStore.get(accountCapsule.getAssetIssuedId());

			assetIssueCapsuleV2.setFreeAssetNetLimit(newLimit);
			assetIssueCapsuleV2.setPublicFreeAssetNetLimit(newPublicLimit);
			assetIssueCapsuleV2.setUrl(newUrl);
			assetIssueCapsuleV2.setDescription(newDescription);

			dbManager.getAssetIssueStore()
				.put(assetIssueCapsuleV2.createDbKey(), assetIssueCapsuleV2);

			ret.setStatus(fee, Code.SUCCESS);
		} catch (InvalidProtocolBufferException e) {
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
		if (!this.contract.is(UpdateAssetContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [UpdateAssetContract],real type[" + contract
					.getClass() + "]");
		}
		final UpdateAssetContract updateAssetContract;
		try {
			updateAssetContract = this.contract.unpack(UpdateAssetContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		long newLimit = updateAssetContract.getNewLimit();
		long newPublicLimit = updateAssetContract.getNewPublicLimit();
		byte[] ownerAddress = updateAssetContract.getOwnerAddress().toByteArray();
		ByteString newUrl = updateAssetContract.getUrl();
		ByteString newDescription = updateAssetContract.getDescription();

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}

		AccountCapsule account = dbManager.getAccountStore().get(ownerAddress);
		if (account == null) {
			throw new ContractValidateException("Account has not existed");
		}

		if (account.getAssetIssuedId() == 0) {
			throw new ContractValidateException("Account has not issue any asset");
		}

		if (dbManager.getAssetIssueStore().get(account.getAssetIssuedId()) == null) {
			throw new ContractValidateException("Asset not exists  in AssetIssueV2Store");
		}

		if (!TransactionUtil.validUrl(newUrl.toByteArray())) {
			throw new ContractValidateException("Invalid url");
		}

		if (!TransactionUtil.validAssetDescription(newDescription.toByteArray())) {
			throw new ContractValidateException("Invalid description");
		}

		if (newLimit < 0 || newLimit >= dbManager.getDynamicPropertiesStore().getOneDayNetLimit()) {
			throw new ContractValidateException("Invalid FreeAssetNetLimit");
		}

		if (newPublicLimit < 0 || newPublicLimit >=
			dbManager.getDynamicPropertiesStore().getOneDayNetLimit()) {
			throw new ContractValidateException("Invalid PublicFreeAssetNetLimit");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(AccountUpdateContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
