package io.midasprotocol.core.db;

import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.BlockCapsule.BlockId;
import io.midasprotocol.core.capsule.BytesCapsule;
import io.midasprotocol.core.exception.ItemNotFoundException;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class BlockIndexStore extends TronStoreWithRevoking<BytesCapsule> {


	@Autowired
	public BlockIndexStore(@Value("block-index") String dbName) {
		super(dbName);

	}

	public void put(BlockId id) {
		put(ByteArray.fromLong(id.getNum()), new BytesCapsule(id.getBytes()));
	}

	public BlockId get(Long num)
		throws ItemNotFoundException {
		BytesCapsule value = getUnchecked(ByteArray.fromLong(num));
		if (value == null || value.getData() == null) {
			throw new ItemNotFoundException("number: " + num + " is not found!");
		}
		return new BlockId(Sha256Hash.wrap(value.getData()), num);
	}

	@Override
	public BytesCapsule get(byte[] key)
		throws ItemNotFoundException {
		byte[] value = revokingDB.getUnchecked(key);
		if (ArrayUtils.isEmpty(value)) {
			throw new ItemNotFoundException("number: " + Arrays.toString(key) + " is not found!");
		}
		return new BytesCapsule(value);
	}
}