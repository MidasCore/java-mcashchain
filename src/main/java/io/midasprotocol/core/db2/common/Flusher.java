package io.midasprotocol.core.db2.common;

import io.midasprotocol.core.db.common.WrappedByteArray;

import java.util.Map;

public interface Flusher {

	void flush(Map<WrappedByteArray, WrappedByteArray> batch);

	void close();

	void reset();
}
