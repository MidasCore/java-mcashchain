package io.midasprotocol.core.db2.core;

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import io.midasprotocol.common.utils.ByteUtil;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.common.WrappedByteArray;
import io.midasprotocol.core.db2.common.*;
import io.midasprotocol.core.exception.ItemNotFoundException;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

public class RevokingDBWithCachingNewValue implements IRevokingDB {

	//true:fullnode, false:soliditynode
	private ThreadLocal<Boolean> mode = new ThreadLocal<>();
	private Snapshot head;
	@Getter
	private String dbName;
	private Class<? extends DB> clz;

	public RevokingDBWithCachingNewValue(String dbName, Class<? extends DB> clz) {
		this.dbName = dbName;
		this.clz = clz;
		head = new SnapshotRoot(Args.getInstance().getOutputDirectoryByDbName(dbName), dbName, clz);
		mode.set(true);
	}

	@Override
	public void setMode(boolean mode) {
		this.mode.set(mode);
	}

	private Snapshot head() {
		if (mode.get() == null || mode.get()) {
			return head;
		} else {
			return head.getSolidity();
		}
	}

	public synchronized Snapshot getHead() {
		return head();
	}

	public synchronized void setHead(Snapshot head) {
		this.head = head;
	}

	/**
	 * close the database.
	 */
	@Override
	public synchronized void close() {
		head().close();
	}

	@Override
	public synchronized void reset() {
		head().reset();
		head().close();
		head = new SnapshotRoot(Args.getInstance().getOutputDirectoryByDbName(dbName), dbName, clz);
	}

	@Override
	public synchronized void put(byte[] key, byte[] value) {
		head().put(key, value);
	}

	@Override
	public synchronized void delete(byte[] key) {
		head().remove(key);
	}

	@Override
	public synchronized byte[] get(byte[] key) throws ItemNotFoundException {
		byte[] value = getUnchecked(key);
		if (value == null) {
			throw new ItemNotFoundException();
		}

		return value;
	}

	@Override
	public synchronized byte[] getUnchecked(byte[] key) {
		return head().get(key);
	}

	@Override
	public synchronized boolean has(byte[] key) {
		return getUnchecked(key) != null;
	}

	@Override
	public synchronized Iterator<Map.Entry<byte[], byte[]>> iterator() {
		return head().iterator();
	}

	//for blockstore
	@Override
	public Set<byte[]> getlatestValues(long limit) {
		return getlatestValues(head(), limit);
	}

	//for blockstore
	private synchronized Set<byte[]> getlatestValues(Snapshot head, long limit) {
		if (limit <= 0) {
			return Collections.emptySet();
		}

		Set<byte[]> result = new HashSet<>();
		Snapshot snapshot = head;
		long tmp = limit;
		for (; tmp > 0 && snapshot.getPrevious() != null; snapshot = snapshot.getPrevious()) {
			if (!((SnapshotImpl) snapshot).db.isEmpty()) {
				--tmp;
				Streams.stream(((SnapshotImpl) snapshot).db)
					.map(Map.Entry::getValue)
					.map(Value::getBytes)
					.forEach(result::add);
			}
		}

		if (snapshot.getPrevious() == null && tmp != 0) {
			if (((SnapshotRoot) head.getRoot()).db.getClass() == LevelDB.class) {
				result.addAll(((LevelDB) ((SnapshotRoot) snapshot).db).getDb().getlatestValues(tmp));
			} else if (((SnapshotRoot) head.getRoot()).db.getClass() == RocksDB.class) {
				result.addAll(((RocksDB) ((SnapshotRoot) snapshot).db).getDb().getlatestValues(tmp));
			}
		}

		return result;
	}

	//for blockstore
	private Set<byte[]> getValuesNext(Snapshot head, byte[] key, long limit) {
		if (limit <= 0) {
			return Collections.emptySet();
		}

		Map<WrappedByteArray, WrappedByteArray> collection = new HashMap<>();
		if (head.getPrevious() != null) {
			((SnapshotImpl) head).collect(collection);
		}

		Map<WrappedByteArray, WrappedByteArray> levelDBMap = new HashMap<>();

		if (((SnapshotRoot) head.getRoot()).db.getClass() == LevelDB.class) {
			((LevelDB) ((SnapshotRoot) head.getRoot()).db).getDb().getNext(key, limit).entrySet().stream()
				.map(e -> Maps
					.immutableEntry(WrappedByteArray.of(e.getKey()), WrappedByteArray.of(e.getValue())))
				.forEach(e -> levelDBMap.put(e.getKey(), e.getValue()));
		} else if (((SnapshotRoot) head.getRoot()).db.getClass() == RocksDB.class) {
			((RocksDB) ((SnapshotRoot) head.getRoot()).db).getDb().getNext(key, limit).entrySet().stream()
				.map(e -> Maps
					.immutableEntry(WrappedByteArray.of(e.getKey()), WrappedByteArray.of(e.getValue())))
				.forEach(e -> levelDBMap.put(e.getKey(), e.getValue()));
		}

		levelDBMap.putAll(collection);

		return levelDBMap.entrySet().stream()
			.sorted((e1, e2) -> ByteUtil.compare(e1.getKey().getBytes(), e2.getKey().getBytes()))
			.filter(e -> ByteUtil.greaterOrEquals(e.getKey().getBytes(), key))
			.limit(limit)
			.map(Map.Entry::getValue)
			.map(WrappedByteArray::getBytes)
			.collect(Collectors.toSet());
	}

	@Override
	public Set<byte[]> getValuesNext(byte[] key, long limit) {
		return getValuesNext(head(), key, limit);
	}
}
