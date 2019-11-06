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

package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.db.AccountStore;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.TransferAssetContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;

@Slf4j(topic = "actuator")
public class TransferAssetActuator extends AbstractActuator {

	TransferAssetActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	public static boolean validateForSmartContract(Deposit deposit, byte[] ownerAddress,
												   byte[] toAddress, long tokenId, long amount) throws ContractValidateException {
		if (deposit == null) {
			throw new ContractValidateException("No deposit!");
		}

		long fee = 0;

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}
		if (!Wallet.addressValid(toAddress)) {
			throw new ContractValidateException("Invalid toAddress");
		}
//    if (!TransactionUtil.validAssetName(assetName)) {
//      throw new ContractValidateException("Invalid assetName");
//    }
		if (amount <= 0) {
			throw new ContractValidateException("Amount must greater than 0.");
		}

		if (Arrays.equals(ownerAddress, toAddress)) {
			throw new ContractValidateException("Cannot transfer asset to yourself.");
		}


		if (Arrays.equals(ownerAddress, deposit.getBlackHoleAddress())) {
			throw new ContractValidateException("Cannot transfer mcash from burn account");
		}

		AccountCapsule ownerAccount = deposit.getAccount(ownerAddress);
		if (ownerAccount == null) {
			throw new ContractValidateException("No owner account!");
		}

		if (deposit.getAssetIssue(tokenId) == null) {
			throw new ContractValidateException("No asset !");
		}
		if (!deposit.getDbManager().getAssetIssueStore().has(tokenId)) {
			throw new ContractValidateException("No asset !");
		}

		Map<Long, Long> asset;
		asset = ownerAccount.getAssetMap();
		if (asset.isEmpty()) {
			throw new ContractValidateException("Owner no asset!");
		}

		Long assetBalance = asset.get(tokenId);
		if (null == assetBalance || assetBalance <= 0) {
			throw new ContractValidateException("assetBalance must greater than 0.");
		}
		if (amount > assetBalance) {
			throw new ContractValidateException("assetBalance is not sufficient.");
		}

		AccountCapsule toAccount = deposit.getAccount(toAddress);
		if (toAccount != null) {
			assetBalance = toAccount.getAssetMap().get(tokenId);
			if (assetBalance != null) {
				try {
					assetBalance = Math.addExact(assetBalance, amount); //check if overflow
				} catch (Exception e) {
					logger.debug(e.getMessage(), e);
					throw new ContractValidateException(e.getMessage());
				}
			}
		} else {
			throw new ContractValidateException(
				"Validate InternalTransfer error, no ToAccount. And not allowed to create account in smart contract.");
		}

		return true;
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			TransferAssetContract transferAssetContract = this.contract
				.unpack(TransferAssetContract.class);
			AccountStore accountStore = this.dbManager.getAccountStore();
			byte[] ownerAddress = transferAssetContract.getOwnerAddress().toByteArray();
			byte[] toAddress = transferAssetContract.getToAddress().toByteArray();
			AccountCapsule toAccountCapsule = accountStore.get(toAddress);
			if (toAccountCapsule == null) {
				boolean withDefaultPermission =
					dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
				toAccountCapsule = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal,
					dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
				dbManager.getAccountStore().put(toAddress, toAccountCapsule);

				fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
			}
			long assetId = transferAssetContract.getAssetId();
			long amount = transferAssetContract.getAmount();

			dbManager.adjustBalance(ownerAddress, -fee);
			dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().createDbKey(), fee);

			AccountCapsule ownerAccountCapsule = accountStore.get(ownerAddress);
			if (!ownerAccountCapsule.reduceAssetAmount(assetId, amount)) {
				throw new ContractExeException("reduceAssetAmount failed !");
			}
			accountStore.put(ownerAddress, ownerAccountCapsule);

			toAccountCapsule.addAssetAmount(assetId, amount);
			accountStore.put(toAddress, toAccountCapsule);

			ret.setStatus(fee, Code.SUCCESS);
		} catch (BalanceInsufficientException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		} catch (InvalidProtocolBufferException e) {
			ret.setStatus(fee, Code.FAILED);
			throw new ContractExeException(e.getMessage());
		} catch (ArithmeticException e) {
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
		if (!this.contract.is(TransferAssetContract.class)) {
			throw new ContractValidateException(
				"contract type error,expected type [TransferAssetContract],real type[" + contract
					.getClass() + "]");
		}
		final TransferAssetContract transferAssetContract;
		try {
			transferAssetContract = this.contract.unpack(TransferAssetContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		long fee = calcFee();
		byte[] ownerAddress = transferAssetContract.getOwnerAddress().toByteArray();
		byte[] toAddress = transferAssetContract.getToAddress().toByteArray();
		long assetId = transferAssetContract.getAssetId();
		long amount = transferAssetContract.getAmount();
		byte[] memo = transferAssetContract.getMemo().toByteArray();

		if (memo.length > Parameter.ChainConstant.MEMO_MAX_LENGTH) {
			throw new ContractValidateException("Invalid memo length");
		}

		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}
		if (!Wallet.addressValid(toAddress)) {
			throw new ContractValidateException("Invalid toAddress");
		}
//    if (!TransactionUtil.validAssetName(assetName)) {
//      throw new ContractValidateException("Invalid assetName");
//    }
		if (amount <= 0) {
			throw new ContractValidateException("Amount must greater than 0.");
		}

		if (Arrays.equals(ownerAddress, toAddress)) {
			throw new ContractValidateException("Cannot transfer asset to yourself.");
		}

		if (dbManager.getAccountStore().getBlackhole().getAddress().equals(transferAssetContract.getOwnerAddress())) {
			throw new ContractValidateException("Cannot transfer mcash from burn account");
		}

		AccountCapsule ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
		if (ownerAccount == null) {
			throw new ContractValidateException("No owner account!");
		}

		if (!this.dbManager.getAssetIssueStore().has(assetId)) {
			throw new ContractValidateException("No asset !");
		}

		Map<Long, Long> asset = ownerAccount.getAssetMap();
		if (asset.isEmpty()) {
			throw new ContractValidateException("Owner no asset!");
		}

		Long assetBalance = asset.get(assetId);
		if (null == assetBalance || assetBalance <= 0) {
			throw new ContractValidateException("assetBalance must greater than 0.");
		}
		if (amount > assetBalance) {
			throw new ContractValidateException("assetBalance is not sufficient.");
		}

		AccountCapsule toAccount = this.dbManager.getAccountStore().get(toAddress);
		if (toAccount != null) {
			assetBalance = toAccount.getAssetMap().get(assetId);
			if (assetBalance != null) {
				try {
					assetBalance = Math.addExact(assetBalance, amount); //check if overflow
				} catch (Exception e) {
					logger.debug(e.getMessage(), e);
					throw new ContractValidateException(e.getMessage());
				}
			}
		} else {
			fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
			if (ownerAccount.getBalance() < fee) {
				throw new ContractValidateException(
					"Validate TransferAssetActuator error, insufficient fee.");
			}
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(TransferAssetContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
