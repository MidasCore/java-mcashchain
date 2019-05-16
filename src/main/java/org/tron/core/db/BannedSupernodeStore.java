package org.tron.core.db;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.BannedSupernodeCapsule;

@Slf4j(topic = "DB")
@Component
public class BannedSupernodeStore extends TronStoreWithRevoking<BannedSupernodeCapsule> {

	@Autowired
	protected BannedSupernodeStore(@Value("banned-supernode") String dbName) {
		super(dbName);
	}

	@Override
	public BannedSupernodeCapsule get(byte[] key) {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new BannedSupernodeCapsule(value);
	}
}
