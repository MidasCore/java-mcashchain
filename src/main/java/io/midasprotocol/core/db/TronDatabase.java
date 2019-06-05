package io.midasprotocol.core.db;

import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.storage.DbSourceInter;
import io.midasprotocol.common.storage.leveldb.LevelDbDataSourceImpl;
import io.midasprotocol.common.storage.leveldb.RocksDbDataSourceImpl;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.api.IndexHelper;
import io.midasprotocol.core.db2.core.ITronChainBase;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.core.exception.ItemNotFoundException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map.Entry;

@Slf4j(topic = "DB")
public abstract class TronDatabase<T> implements ITronChainBase<T> {

	protected DbSourceInter<byte[]> dbSource;
	@Autowired(required = false)
	protected IndexHelper indexHelper;
	@Getter
	private String dbName;

	protected TronDatabase(String dbName) {
		this.dbName = dbName;

		if ("LEVELDB".equals(Args.getInstance().getStorage().getDbEngine().toUpperCase())) {
			dbSource =
				new LevelDbDataSourceImpl(Args.getInstance().getOutputDirectoryByDbName(dbName), dbName);
		} else if ("ROCKSDB".equals(Args.getInstance().getStorage().getDbEngine().toUpperCase())) {
			String parentName = Paths.get(Args.getInstance().getOutputDirectoryByDbName(dbName),
				Args.getInstance().getStorage().getDbDirectory()).toString();
			dbSource =
				new RocksDbDataSourceImpl(parentName, dbName);
		}

		dbSource.initDB();
	}

	protected TronDatabase() {
	}

	public DbSourceInter<byte[]> getDbSource() {
		return dbSource;
	}

	/**
	 * reset the database.
	 */
	public void reset() {
		dbSource.resetDb();
	}

	/**
	 * close the database.
	 */
	@Override
	public void close() {
		dbSource.closeDB();
	}

	public abstract void put(byte[] key, T item);

	public abstract void delete(byte[] key);

	public abstract T get(byte[] key)
		throws InvalidProtocolBufferException, ItemNotFoundException, BadItemException;

	public T getUnchecked(byte[] key) {
		return null;
	}

	public abstract boolean has(byte[] key);

	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public Iterator<Entry<byte[], T>> iterator() {
		throw new UnsupportedOperationException();
	}
}
