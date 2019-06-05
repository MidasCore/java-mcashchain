package io.midasprotocol.core.db;

import io.midasprotocol.core.capsule.StorageRowCapsule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j(topic = "DB")
@Component
public class StorageRowStore extends TronStoreWithRevoking<StorageRowCapsule> {

	private static StorageRowStore instance;

	@Autowired
	private StorageRowStore(@Value("storage-row") String dbName) {
		super(dbName);
	}

	@Override
	public StorageRowCapsule get(byte[] key) {
		StorageRowCapsule row = getUnchecked(key);
		row.setRowKey(key);
		return row;
	}

	void destory() {
		instance = null;
	}
}
