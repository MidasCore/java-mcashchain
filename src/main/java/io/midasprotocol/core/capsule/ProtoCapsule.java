package io.midasprotocol.core.capsule;

public interface ProtoCapsule<T> {

	byte[] getData();

	T getInstance();
}
