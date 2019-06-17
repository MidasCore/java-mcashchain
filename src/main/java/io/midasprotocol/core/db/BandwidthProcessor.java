package io.midasprotocol.core.db;

import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.config.Parameter;
import io.midasprotocol.core.exception.AccountResourceInsufficientException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.TooBigTransactionResultException;
import io.midasprotocol.protos.Contract.TransferAssetContract;
import io.midasprotocol.protos.Contract.TransferContract;
import io.midasprotocol.protos.Protocol.Transaction.Contract;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType.TransferAssetContract;

@Slf4j(topic = "DB")
public class BandwidthProcessor extends ResourceProcessor {

	public BandwidthProcessor(Manager manager) {
		super(manager);
	}

	@Override
	public void updateUsage(AccountCapsule accountCapsule) {
		long now = dbManager.getWitnessController().getHeadSlot();
		updateUsage(accountCapsule, now);
	}

	private void updateUsage(AccountCapsule accountCapsule, long now) {
		long oldNetUsage = accountCapsule.getBandwidthUsage();
		long latestConsumeTime = accountCapsule.getLatestBandwidthConsumeTime();
		accountCapsule.setBandwidthUsage(increase(oldNetUsage, 0, latestConsumeTime, now));
		long oldFreeNetUsage = accountCapsule.getFreeBandwidthUsage();
		long latestConsumeFreeTime = accountCapsule.getLatestFreeBandwidthConsumeTime();
		accountCapsule.setFreeBandwidthUsage(increase(oldFreeNetUsage, 0, latestConsumeFreeTime, now));

		Map<Long, Long> assetMapV2 = accountCapsule.getAssetMap();
		assetMapV2.forEach((assetName, balance) -> {
			long oldFreeAssetNetUsage = accountCapsule.getFreeAssetBandwidthUsage(assetName);
			long latestAssetOperationTime = accountCapsule.getLatestAssetOperationTime(assetName);
			accountCapsule.putFreeAssetBandwidthUsage(assetName,
				increase(oldFreeAssetNetUsage, 0, latestAssetOperationTime, now));
		});
	}

	@Override
	public void consume(TransactionCapsule trx, TransactionTrace trace)
		throws ContractValidateException, AccountResourceInsufficientException, TooBigTransactionResultException {
		List<Contract> contracts = trx.getInstance().getRawData().getContractList();
		if (trx.getResultSerializedSize() > Constant.MAX_RESULT_SIZE_IN_TX * contracts.size()) {
			throw new TooBigTransactionResultException();
		}

		long bytesSize;
		if (dbManager.getDynamicPropertiesStore().supportVM()) {
			bytesSize = trx.getInstance().toBuilder().clearRet().build().getSerializedSize();
		} else {
			bytesSize = trx.getSerializedSize();
		}

		for (Contract contract : contracts) {
			if (dbManager.getDynamicPropertiesStore().supportVM()) {
				bytesSize += Constant.MAX_RESULT_SIZE_IN_TX;
			}

			logger.debug("trxId {},bandwidth cost :{}", trx.getTransactionId(), bytesSize);
			trace.setNetBill(bytesSize, 0);
			byte[] address = TransactionCapsule.getOwner(contract);
			AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
			if (accountCapsule == null) {
				throw new ContractValidateException("account not exists");
			}
			long now = dbManager.getWitnessController().getHeadSlot();

			if (contractCreateNewAccount(contract)) {
				consumeForCreateNewAccount(accountCapsule, bytesSize, now, trace);
				continue;
			}

			if (contract.getType() == TransferAssetContract && useAssetAccountNet(contract,
				accountCapsule, now, bytesSize)) {
				continue;
			}

			if (useAccountNet(accountCapsule, bytesSize, now)) {
				continue;
			}

			if (useFreeNet(accountCapsule, bytesSize, now)) {
				continue;
			}

			if (useTransactionFee(accountCapsule, bytesSize, trace)) {
				continue;
			}

			long fee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytesSize;
			throw new AccountResourceInsufficientException(
				"Account Insufficient bandwidth[" + bytesSize + "] and balance["
					+ fee + "] to create new account");
		}
	}

	private boolean useTransactionFee(AccountCapsule accountCapsule, long bytes,
									  TransactionTrace trace) {
		long fee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytes;
		if (consumeFee(accountCapsule, fee)) {
			trace.setNetBill(0, fee);
			dbManager.getDynamicPropertiesStore().addTotalTransactionCost(fee);
			return true;
		} else {
			return false;
		}
	}

	private void consumeForCreateNewAccount(AccountCapsule accountCapsule, long bytes,
											long now, TransactionTrace trace)
		throws AccountResourceInsufficientException {
		boolean ret = consumeBandwidthForCreateNewAccount(accountCapsule, bytes, now);

		if (!ret) {
			ret = consumeFeeForCreateNewAccount(accountCapsule, trace);
			if (!ret) {
				throw new AccountResourceInsufficientException();
			}
		}
	}

	public boolean consumeBandwidthForCreateNewAccount(AccountCapsule accountCapsule, long bytes,
													   long now) {

		long createNewAccountBandwidthRatio = dbManager.getDynamicPropertiesStore()
			.getCreateNewAccountBandwidthRate();

		long netUsage = accountCapsule.getBandwidthUsage();
		long latestConsumeTime = accountCapsule.getLatestBandwidthConsumeTime();
		long netLimit = calculateGlobalBandwidthLimit(accountCapsule);

		long newNetUsage = increase(netUsage, 0, latestConsumeTime, now);

		if (bytes * createNewAccountBandwidthRatio <= (netLimit - newNetUsage)) {
			latestConsumeTime = now;
			long latestOperationTime = dbManager.getHeadBlockTimeStamp();
			newNetUsage = increase(newNetUsage, bytes * createNewAccountBandwidthRatio, latestConsumeTime,
				now);
			accountCapsule.setLatestBandwidthConsumeTime(latestConsumeTime);
			accountCapsule.setLatestOperationTime(latestOperationTime);
			accountCapsule.setBandwidthUsage(newNetUsage);
			dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
			return true;
		}
		return false;
	}

	public boolean consumeFeeForCreateNewAccount(AccountCapsule accountCapsule,
												 TransactionTrace trace) {
		long fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
		if (consumeFee(accountCapsule, fee)) {
			trace.setNetBill(0, fee);
			dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
			return true;
		} else {
			return false;
		}
	}

	public boolean contractCreateNewAccount(Contract contract) {
		AccountCapsule toAccount;
		switch (contract.getType()) {
			case AccountCreateContract:
				return true;
			case TransferContract:
				TransferContract transferContract;
				try {
					transferContract = contract.getParameter().unpack(TransferContract.class);
				} catch (Exception ex) {
					throw new RuntimeException(ex.getMessage());
				}
				toAccount = dbManager.getAccountStore().get(transferContract.getToAddress().toByteArray());
				return toAccount == null;
			case TransferAssetContract:
				TransferAssetContract transferAssetContract;
				try {
					transferAssetContract = contract.getParameter().unpack(TransferAssetContract.class);
				} catch (Exception ex) {
					throw new RuntimeException(ex.getMessage());
				}
				toAccount = dbManager.getAccountStore()
					.get(transferAssetContract.getToAddress().toByteArray());
				return toAccount == null;
			default:
				return false;
		}
	}


	private boolean useAssetAccountNet(Contract contract, AccountCapsule accountCapsule, long now,
									   long bytes)
		throws ContractValidateException {

		long assetId;
		try {
			assetId = contract.getParameter().unpack(TransferAssetContract.class).getAssetId();
		} catch (Exception ex) {
			throw new RuntimeException(ex.getMessage());
		}

		AssetIssueCapsule assetIssueCapsule;
		assetIssueCapsule = dbManager.getAssetIssueStore().get(assetId);
		if (assetIssueCapsule == null) {
			throw new ContractValidateException("asset not exists");
		}
		long tokenID = assetIssueCapsule.getId();
		if (assetIssueCapsule.getOwnerAddress() == accountCapsule.getAddress()) {
			return useAccountNet(accountCapsule, bytes, now);
		}

		long publicFreeAssetNetLimit = assetIssueCapsule.getPublicFreeAssetBandwidthLimit();
		long publicFreeAssetNetUsage = assetIssueCapsule.getPublicFreeAssetBandwidthUsage();
		long publicLatestFreeNetTime = assetIssueCapsule.getPublicLatestFreeBandwidthTime();

		long newPublicFreeAssetNetUsage = increase(publicFreeAssetNetUsage, 0,
			publicLatestFreeNetTime, now);

		if (bytes > (publicFreeAssetNetLimit - newPublicFreeAssetNetUsage)) {
			logger.debug("The " + tokenID + " public free bandwidth is not enough");
			return false;
		}

		long freeAssetNetLimit = assetIssueCapsule.getFreeAssetBandwidthLimit();

		long freeAssetNetUsage, latestAssetOperationTime;
		freeAssetNetUsage = accountCapsule.getFreeAssetBandwidthUsage(tokenID);
		latestAssetOperationTime = accountCapsule.getLatestAssetOperationTime(tokenID);

		long newFreeAssetNetUsage = increase(freeAssetNetUsage, 0,
			latestAssetOperationTime, now);

		if (bytes > (freeAssetNetLimit - newFreeAssetNetUsage)) {
			logger.debug("The " + tokenID + " free bandwidth is not enough");
			return false;
		}

		AccountCapsule issuerAccountCapsule = dbManager.getAccountStore()
			.get(assetIssueCapsule.getOwnerAddress().toByteArray());

		long issuerNetUsage = issuerAccountCapsule.getBandwidthUsage();
		long latestConsumeTime = issuerAccountCapsule.getLatestBandwidthConsumeTime();
		long issuerNetLimit = calculateGlobalBandwidthLimit(issuerAccountCapsule);

		long newIssuerNetUsage = increase(issuerNetUsage, 0, latestConsumeTime, now);

		if (bytes > (issuerNetLimit - newIssuerNetUsage)) {
			logger.debug("The " + tokenID + " issuer'bandwidth is not enough");
			return false;
		}

		latestConsumeTime = now;
		latestAssetOperationTime = now;
		publicLatestFreeNetTime = now;
		long latestOperationTime = dbManager.getHeadBlockTimeStamp();

		newIssuerNetUsage = increase(newIssuerNetUsage, bytes, latestConsumeTime, now);
		newFreeAssetNetUsage = increase(newFreeAssetNetUsage,
			bytes, latestAssetOperationTime, now);
		newPublicFreeAssetNetUsage = increase(newPublicFreeAssetNetUsage, bytes,
			publicLatestFreeNetTime, now);

		issuerAccountCapsule.setBandwidthUsage(newIssuerNetUsage);
		issuerAccountCapsule.setLatestBandwidthConsumeTime(latestConsumeTime);

		assetIssueCapsule.setPublicFreeAssetBandwidthUsage(newPublicFreeAssetNetUsage);
		assetIssueCapsule.setPublicLatestFreeBandwidthTime(publicLatestFreeNetTime);

		accountCapsule.setLatestOperationTime(latestOperationTime);
		accountCapsule.putLatestAssetOperationTimeMap(tokenID,
			latestAssetOperationTime);
		accountCapsule.putFreeAssetBandwidthUsage(tokenID, newFreeAssetNetUsage);
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		dbManager.getAccountStore().put(issuerAccountCapsule.createDbKey(),
			issuerAccountCapsule);

		return true;

	}

	public long calculateGlobalBandwidthLimit(AccountCapsule accountCapsule) {
		long frozeBalance = accountCapsule.getAllFrozenBalanceForBandwidth();
		if (frozeBalance < Parameter.ChainConstant.TEN_POW_DECIMALS) {
			return 0;
		}
		long netWeight = frozeBalance / Parameter.ChainConstant.TEN_POW_DECIMALS;
		long totalNetLimit = dbManager.getDynamicPropertiesStore().getTotalBandwidthLimit();
		long totalNetWeight = dbManager.getDynamicPropertiesStore().getTotalBandwidthWeight();
		if (totalNetWeight == 0) {
			return 0;
		}
		return (long) (netWeight * ((double) totalNetLimit / totalNetWeight));
	}

	private boolean useAccountNet(AccountCapsule accountCapsule, long bytes, long now) {

		long netUsage = accountCapsule.getBandwidthUsage();
		long latestConsumeTime = accountCapsule.getLatestBandwidthConsumeTime();
		long netLimit = calculateGlobalBandwidthLimit(accountCapsule);

		long newNetUsage = increase(netUsage, 0, latestConsumeTime, now);

		if (bytes > (netLimit - newNetUsage)) {
			logger.debug("net usage is running out. now use free net usage");
			return false;
		}

		latestConsumeTime = now;
		long latestOperationTime = dbManager.getHeadBlockTimeStamp();
		newNetUsage = increase(newNetUsage, bytes, latestConsumeTime, now);
		accountCapsule.setBandwidthUsage(newNetUsage);
		accountCapsule.setLatestOperationTime(latestOperationTime);
		accountCapsule.setLatestBandwidthConsumeTime(latestConsumeTime);

		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		return true;
	}

	private boolean useFreeNet(AccountCapsule accountCapsule, long bytes, long now) {

		long freeNetLimit = dbManager.getDynamicPropertiesStore().getFreeBandwidthLimit();
		long freeNetUsage = accountCapsule.getFreeBandwidthUsage();
		long latestConsumeFreeTime = accountCapsule.getLatestFreeBandwidthConsumeTime();
		long newFreeNetUsage = increase(freeNetUsage, 0, latestConsumeFreeTime, now);

		if (bytes > (freeNetLimit - newFreeNetUsage)) {
			logger.debug("free net usage is running out");
			return false;
		}

		long publicNetLimit = dbManager.getDynamicPropertiesStore().getPublicBandwidthLimit();
		long publicNetUsage = dbManager.getDynamicPropertiesStore().getPublicBandwidthUsage();
		long publicNetTime = dbManager.getDynamicPropertiesStore().getPublicBandwidthTime();

		long newPublicNetUsage = increase(publicNetUsage, 0, publicNetTime, now);

		if (bytes > (publicNetLimit - newPublicNetUsage)) {
			logger.debug("free public net usage is running out");
			return false;
		}

		latestConsumeFreeTime = now;
		long latestOperationTime = dbManager.getHeadBlockTimeStamp();
		publicNetTime = now;
		newFreeNetUsage = increase(newFreeNetUsage, bytes, latestConsumeFreeTime, now);
		newPublicNetUsage = increase(newPublicNetUsage, bytes, publicNetTime, now);
		accountCapsule.setFreeBandwidthUsage(newFreeNetUsage);
		accountCapsule.setLatestFreeBandwidthConsumeTime(latestConsumeFreeTime);
		accountCapsule.setLatestOperationTime(latestOperationTime);

		dbManager.getDynamicPropertiesStore().savePublicBandwidthUsage(newPublicNetUsage);
		dbManager.getDynamicPropertiesStore().savePublicBandwidthTime(publicNetTime);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
		return true;

	}

}


