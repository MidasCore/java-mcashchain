package io.midasprotocol.core.db2.common;

import com.google.common.collect.Maps;
import io.midasprotocol.common.storage.WriteOptionsWrapper;
import io.midasprotocol.common.storage.leveldb.LevelDbDataSourceImpl;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.common.WrappedByteArray;
import io.midasprotocol.core.db.common.iterator.DBIterator;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class LevelDB implements DB<byte[], byte[]>, Flusher {

	@Getter
	private LevelDbDataSourceImpl db;
	private WriteOptionsWrapper writeOptions = WriteOptionsWrapper.getInstance()
		.sync(Args.getInstance().getStorage().isDbSync());

	public LevelDB(String parentName, String name) {
		db = new LevelDbDataSourceImpl(parentName, name);
		db.initDB();
	}

	@Override
	public byte[] get(byte[] key) {
		return db.getData(key);
	}

	@Override
	public void put(byte[] key, byte[] value) {
		db.putData(key, value);
	}

	@Override
	public long size() {
		return db.getTotal();
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public void remove(byte[] key) {
		db.deleteData(key);
	}

	@Override
	public DBIterator iterator() {
		return db.iterator();
	}

	@Override
	public void flush(Map<WrappedByteArray, WrappedByteArray> batch) {
		Map<byte[], byte[]> rows = batch.entrySet().stream()
			.map(e -> Maps.immutableEntry(e.getKey().getBytes(), e.getValue().getBytes()))
			.collect(HashMap::new, (m, k) -> m.put(k.getKey(), k.getValue()), HashMap::putAll);
		db.updateByBatch(rows, writeOptions);
//    db.reOpen();
	}

	@Override
	public void close() {
		db.closeDB();
	}

	@Override
	public void reset() {
		db.resetDb();
	}
}
