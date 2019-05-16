package io.midasprotocol.core.db;

import io.midasprotocol.core.capsule.BlockRewardCapsule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j(topic = "DB")
@Component
public class BlockRewardStore extends TronStoreWithRevoking<BlockRewardCapsule> {

	@Autowired
	private BlockRewardStore(@Value("block-reward") String dbName) {
		super(dbName);
	}

	@Override
	public BlockRewardCapsule get(byte[] key) {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new BlockRewardCapsule(value);
	}
}
