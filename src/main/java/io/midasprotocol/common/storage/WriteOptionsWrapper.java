package io.midasprotocol.common.storage;

public class WriteOptionsWrapper {

	public org.rocksdb.WriteOptions rocks = null;
	public org.iq80.leveldb.WriteOptions level = null;

	private WriteOptionsWrapper() {

	}

	public static WriteOptionsWrapper getInstance() {
		WriteOptionsWrapper wapper = new WriteOptionsWrapper();
		wapper.level = new org.iq80.leveldb.WriteOptions();
		wapper.rocks = new org.rocksdb.WriteOptions();
		return wapper;
	}


	public WriteOptionsWrapper sync(boolean bool) {
		this.level.sync(bool);
		this.rocks.setSync(bool);
		return this;
	}
}