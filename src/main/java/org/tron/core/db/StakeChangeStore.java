package org.tron.core.db;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.StakeChangeCapsule;

@Component
public class StakeChangeStore extends TronStoreWithRevoking<StakeChangeCapsule> {
	@Autowired
	protected StakeChangeStore(@Value("stake-change") String dbName) {
		super(dbName);
	}

	@Override
	public StakeChangeCapsule get(byte[] key) {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new StakeChangeCapsule(value);
	}
}
