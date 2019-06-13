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
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.ParticipateAssetIssueContract;
import io.midasprotocol.protos.Protocol;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ParticipateAssetIssueActuator extends AbstractActuator {

	ParticipateAssetIssueActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			final ParticipateAssetIssueContract participateAssetIssueContract =
				contract.unpack(Contract.ParticipateAssetIssueContract.class);
			long cost = participateAssetIssueContract.getAmount();

			//subtract from owner address
			byte[] ownerAddress = participateAssetIssueContract.getOwnerAddress().toByteArray();
			AccountCapsule ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
			long balance = Math.subtractExact(ownerAccount.getBalance(), cost);
			balance = Math.subtractExact(balance, fee);
			ownerAccount.setBalance(balance);
			long key = participateAssetIssueContract.getAssetId();

			//calculate the exchange amount
			AssetIssueCapsule assetIssueCapsule;
			assetIssueCapsule = this.dbManager.getAssetIssueStore().get(key);

			long exchangeAmount = Math.multiplyExact(cost, assetIssueCapsule.getNum());
			exchangeAmount = Math.floorDiv(exchangeAmount, assetIssueCapsule.getMcashNum());
			ownerAccount.addAssetAmountV2(key, exchangeAmount);

			//add to to_address
			byte[] toAddress = participateAssetIssueContract.getToAddress().toByteArray();
			AccountCapsule toAccount = this.dbManager.getAccountStore().get(toAddress);
			toAccount.setBalance(Math.addExact(toAccount.getBalance(), cost));
			if (!toAccount.reduceAssetAmountV2(key, exchangeAmount)) {
				throw new ContractExeException("reduceAssetAmount failed !");
			}

			//write to db
			dbManager.getAccountStore().put(ownerAddress, ownerAccount);
			dbManager.getAccountStore().put(toAddress, toAccount);
			ret.setStatus(fee, Protocol.Transaction.Result.Code.SUCCESS);
		} catch (InvalidProtocolBufferException | ArithmeticException e) {
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
		if (!this.contract.is(ParticipateAssetIssueContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected ParticipateAssetIssueContract, actual " + contract.getClass());
		}

		final ParticipateAssetIssueContract participateAssetIssueContract;
		try {
			participateAssetIssueContract =
				this.contract.unpack(ParticipateAssetIssueContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		//Parameters check
		byte[] ownerAddress = participateAssetIssueContract.getOwnerAddress().toByteArray();
		byte[] toAddress = participateAssetIssueContract.getToAddress().toByteArray();
		long assetId = participateAssetIssueContract.getAssetId();
		long amount = participateAssetIssueContract.getAmount();

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
			throw new ContractValidateException("Amount must greater than 0");
		}

		if (Arrays.equals(ownerAddress, toAddress)) {
			throw new ContractValidateException("Cannot participate asset issue yourself");
		}

		//Whether the account exist
		AccountCapsule ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
		if (ownerAccount == null) {
			throw new ContractValidateException("Account does not exist");
		}
		try {
			//Whether the balance is enough
			long fee = calcFee();
			if (ownerAccount.getBalance() < Math.addExact(amount, fee)) {
				throw new ContractValidateException("Not enough balance");
			}

			//Whether have the mapping
			AssetIssueCapsule assetIssueCapsule;
			assetIssueCapsule = this.dbManager.getAssetIssueStore().get(assetId);
			if (assetIssueCapsule == null) {
				throw new ContractValidateException("No asset with id " + assetId);
			}

			if (!Arrays.equals(toAddress, assetIssueCapsule.getOwnerAddress().toByteArray())) {
				throw new ContractValidateException(
					"The asset is not issued by " + ByteArray.toHexString(toAddress));
			}
			//Whether the exchange can be processed: to see if the exchange can be the exact int
			long now = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
			if (now >= assetIssueCapsule.getEndTime() || now < assetIssueCapsule.getStartTime()) {
				throw new ContractValidateException("No longer valid period");
			}

			int trxNum = assetIssueCapsule.getMcashNum();
			int num = assetIssueCapsule.getNum();
			long exchangeAmount = Math.multiplyExact(amount, num);
			exchangeAmount = Math.floorDiv(exchangeAmount, trxNum);
			if (exchangeAmount <= 0) {
				throw new ContractValidateException("Can not process the exchange");
			}

			AccountCapsule toAccount = this.dbManager.getAccountStore().get(toAddress);
			if (toAccount == null) {
				throw new ContractValidateException("To account does not exist");
			}

			if (!toAccount.assetBalanceEnoughV2(assetId, exchangeAmount)) {
				throw new ContractValidateException("Asset balance is not enough");
			}
		} catch (ArithmeticException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return this.contract.unpack(Contract.ParticipateAssetIssueContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}
}
