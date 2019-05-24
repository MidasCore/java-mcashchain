package io.midasprotocol.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.midasprotocol.core.capsule.ContractCapsule;
import io.midasprotocol.protos.Protocol.SmartContract;

@Slf4j(topic = "DB")
@Component
public class ContractStore extends TronStoreWithRevoking<ContractCapsule> {

	private static ContractStore instance;

	@Autowired
	private ContractStore(@Value("contract") String dbName) {
		super(dbName);
	}

	@Override
	public ContractCapsule get(byte[] key) {
		return getUnchecked(key);
	}

	/**
	 * get total transaction.
	 */
	public long getTotalContracts() {
		return Streams.stream(revokingDB.iterator()).count();
	}

	void destroy() {
		instance = null;
	}

	/**
	 * find a transaction  by it's id.
	 */
	public byte[] findContractByHash(byte[] trxHash) {
		return revokingDB.getUnchecked(trxHash);
	}

	/**
	 * @param contractAddress
	 * @return
	 */
	public SmartContract.ABI getABI(byte[] contractAddress) {
		byte[] value = revokingDB.getUnchecked(contractAddress);
		if (ArrayUtils.isEmpty(value)) {
			return null;
		}

		ContractCapsule contractCapsule = new ContractCapsule(value);
		SmartContract smartContract = contractCapsule.getInstance();
		if (smartContract == null) {
			return null;
		}

		return smartContract.getAbi();
	}

}
