package io.midasprotocol.core.db.common.iterator;

import io.midasprotocol.core.capsule.AssetIssueCapsule;

import java.util.Iterator;
import java.util.Map.Entry;

public class AssetIssueIterator extends AbstractIterator<AssetIssueCapsule> {

	public AssetIssueIterator(Iterator<Entry<byte[], byte[]>> iterator) {
		super(iterator);
	}

	@Override
	protected AssetIssueCapsule of(byte[] value) {
		return new AssetIssueCapsule(value);
	}
}