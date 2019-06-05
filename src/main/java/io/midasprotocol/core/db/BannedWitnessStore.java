package io.midasprotocol.core.db;

import io.midasprotocol.core.capsule.BannedWitnessCapsule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j(topic = "DB")
@Component
public class BannedWitnessStore extends TronStoreWithRevoking<BannedWitnessCapsule> {

	@Autowired
	protected BannedWitnessStore(@Value("banned-witness") String dbName) {
		super(dbName);
	}

	@Override
	public BannedWitnessCapsule get(byte[] key) {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new BannedWitnessCapsule(value);
	}
}
