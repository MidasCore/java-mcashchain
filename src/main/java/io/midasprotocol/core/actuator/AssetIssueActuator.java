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
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.capsule.utils.TransactionUtil;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.BalanceInsufficientException;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Contract.AssetIssueContract.FrozenSupply;
import io.midasprotocol.protos.Protocol.Account.Frozen;
import io.midasprotocol.protos.Protocol.Transaction.Result.Code;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j(topic = "actuator")
public class AssetIssueActuator extends AbstractActuator {

	AssetIssueActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		try {
			AssetIssueContract assetIssueContract = contract.unpack(AssetIssueContract.class);
			byte[] ownerAddress = assetIssueContract.getOwnerAddress().toByteArray();
			AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);

			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			tokenIdNum++;
			assetIssueCapsule.setId(tokenIdNum);
			dbManager.getDynamicPropertiesStore().saveTokenIdNum(tokenIdNum);
			dbManager.getAssetIssueStore()
				.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
			dbManager.adjustBalance(ownerAddress, -fee);
			//send fee to blackhole
			dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().getAddress().toByteArray(), fee);

			AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
			List<FrozenSupply> frozenSupplyList = assetIssueContract.getFrozenSupplyList();
			Iterator<FrozenSupply> iterator = frozenSupplyList.iterator();
			long remainSupply = assetIssueContract.getTotalSupply();
			List<Frozen> frozenList = new ArrayList<>();
			long startTime = assetIssueContract.getStartTime();

			while (iterator.hasNext()) {
				FrozenSupply next = iterator.next();
				long expireTime = startTime + next.getFrozenDays() * Parameter.TimeConstant.MS_PER_DAY;
				Frozen newFrozen = Frozen.newBuilder()
					.setFrozenBalance(next.getFrozenAmount())
					.setExpireTime(expireTime)
					.build();
				frozenList.add(newFrozen);
				remainSupply -= next.getFrozenAmount();
			}

			accountCapsule.addAsset(assetIssueCapsule.getId(), remainSupply);
			accountCapsule.setAssetIssuedId(assetIssueCapsule.getId());
			accountCapsule.setInstance(accountCapsule.getInstance().toBuilder().addAllFrozenAssets(frozenList).build());

			dbManager.getAccountStore().put(ownerAddress, accountCapsule);

			ret.setAssetIssueId(tokenIdNum);
			ret.setStatus(fee, Code.SUCCESS);
		} catch (InvalidProtocolBufferException | ArithmeticException | BalanceInsufficientException e) {
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
		if (!this.contract.is(AssetIssueContract.class)) {
			throw new ContractValidateException(
				"Contract type error, expected AssetIssueContract, actual " + contract.getClass());
		}

		final AssetIssueContract assetIssueContract;
		try {
			assetIssueContract = this.contract.unpack(AssetIssueContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}

		byte[] ownerAddress = assetIssueContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid ownerAddress");
		}

		if (!TransactionUtil.validAssetName(assetIssueContract.getName().toByteArray())) {
			throw new ContractValidateException("Invalid assetName");
		}

		String name = assetIssueContract.getName().toStringUtf8().toLowerCase();
		if (name.equals("mcash")) {
			throw new ContractValidateException("Asset name can't be mcash");
		}

		int precision = assetIssueContract.getPrecision();
		if (precision < 0 || precision > 8) {
			throw new ContractValidateException("Precision cannot exceed 8");
		}

		if ((!assetIssueContract.getAbbr().isEmpty()) && !TransactionUtil
			.validAssetName(assetIssueContract.getAbbr().toByteArray())) {
			throw new ContractValidateException("Invalid abbreviation for token");
		}

		if (!TransactionUtil.validUrl(assetIssueContract.getUrl().toByteArray())) {
			throw new ContractValidateException("Invalid url");
		}

		if (!TransactionUtil
			.validAssetDescription(assetIssueContract.getDescription().toByteArray())) {
			throw new ContractValidateException("Invalid description");
		}

		if (assetIssueContract.getStartTime() == 0) {
			throw new ContractValidateException("Start time should be not empty");
		}
		if (assetIssueContract.getEndTime() == 0) {
			throw new ContractValidateException("End time should be not empty");
		}
		if (assetIssueContract.getEndTime() <= assetIssueContract.getStartTime()) {
			throw new ContractValidateException("End time should be greater than start time");
		}
		if (assetIssueContract.getStartTime() <= dbManager.getHeadBlockTimeStamp()) {
			throw new ContractValidateException("Start time should be greater than HeadBlockTime");
		}

		if (assetIssueContract.getTotalSupply() <= 0) {
			throw new ContractValidateException("TotalSupply must greater than 0!");
		}

		if (assetIssueContract.getMcashNum() <= 0) {
			throw new ContractValidateException("McashNum must greater than 0!");
		}

		if (assetIssueContract.getNum() <= 0) {
			throw new ContractValidateException("Num must greater than 0!");
		}

		if (assetIssueContract.getPublicFreeAssetBandwidthUsage() != 0) {
			throw new ContractValidateException("PublicFreeAssetBandwidthUsage must be 0!");
		}

		if (assetIssueContract.getFrozenSupplyCount()
			> this.dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyNumber()) {
			throw new ContractValidateException("Frozen supply list length is too long");
		}

		if (assetIssueContract.getFreeAssetBandwidthLimit() < 0
			|| assetIssueContract.getFreeAssetBandwidthLimit() >=
			dbManager.getDynamicPropertiesStore().getOneDayBandwidthLimit()) {
			throw new ContractValidateException("Invalid FreeAssetBandwidthLimit");
		}

		if (assetIssueContract.getPublicFreeAssetBandwidthLimit() < 0
			|| assetIssueContract.getPublicFreeAssetBandwidthLimit() >=
			dbManager.getDynamicPropertiesStore().getOneDayBandwidthLimit()) {
			throw new ContractValidateException("Invalid PublicFreeAssetBandwidthLimit");
		}

		long remainSupply = assetIssueContract.getTotalSupply();
		long minFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMinFrozenSupplyTime();
		long maxFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyTime();
		List<FrozenSupply> frozenList = assetIssueContract.getFrozenSupplyList();
		Iterator<FrozenSupply> iterator = frozenList.iterator();

		boolean needCheckFrozenTime = Args.getInstance().getCheckFrozenTime() == 1;//for test
		while (iterator.hasNext()) {
			FrozenSupply next = iterator.next();
			if (next.getFrozenAmount() <= 0) {
				throw new ContractValidateException("Frozen supply must be greater than 0!");
			}
			if (next.getFrozenAmount() > remainSupply) {
				throw new ContractValidateException("Frozen supply cannot exceed total supply");
			}
			if (needCheckFrozenTime && !(next.getFrozenDays() >= minFrozenSupplyTime
				&& next.getFrozenDays() <= maxFrozenSupplyTime)) {
				throw new ContractValidateException(
					"frozenDuration must be less than " + maxFrozenSupplyTime + " days "
						+ "and more than " + minFrozenSupplyTime + " days");
			}
			remainSupply -= next.getFrozenAmount();
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			throw new ContractValidateException("Account not exists");
		}

		if (accountCapsule.getAssetIssuedId() > 0) {
			throw new ContractValidateException("An account can only issue one asset");
		}

		if (accountCapsule.getBalance() < calcFee()) {
			throw new ContractValidateException("No enough balance for fee!");
		}
//
//    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
//    String name = new String(assetIssueCapsule.getName().toByteArray(),
//        Charset.forName("UTF-8")); // getName().toStringUtf8()
//    long order = 0;
//    byte[] key = name.getBytes();
//    while (this.dbManager.getAssetIssueStore().get(key) != null) {
//      order++;
//      String nameKey = AssetIssueCapsule.createDbKeyString(name, order);
//      key = nameKey.getBytes();
//    }
//    assetIssueCapsule.setOrder(order);
//
//    if (!TransactionUtil.validAssetName(assetIssueCapsule.createDbKey())) {
//      throw new ContractValidateException("Invalid assetID");
//    }

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(AssetIssueContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return dbManager.getDynamicPropertiesStore().getAssetIssueFee();
	}

	public long calcUsage() {
		return 0;
	}
}
