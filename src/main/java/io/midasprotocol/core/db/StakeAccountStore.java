package io.midasprotocol.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.midasprotocol.core.capsule.StakeAccountCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class StakeAccountStore extends TronStoreWithRevoking<StakeAccountCapsule> {

	@Autowired
	private StakeAccountStore(@Value("staking-node") String dbName) {
		super(dbName);
	}

	@Override
	public StakeAccountCapsule get(byte[] key) {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new StakeAccountCapsule(value);
	}

	/**
	 * get all witnesses.
	 */
	public List<StakeAccountCapsule> getAllStakeAccounts() {
		return Streams.stream(iterator())
				.map(Entry::getValue)
				.collect(Collectors.toList());
	}


}
