package org.tron.common.storage;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public class Key {

	private static int MAX_KEY_LENGTH = 32;
	private static int MIN_KEY_LENGTH = 1;

	/**
	 * data could not be null
	 */
	private byte[] data = new byte[0];

	/**
	 * @param data
	 */
	public Key(byte[] data) {
		if (data != null && data.length != 0) {
			this.data = new byte[data.length];
			System.arraycopy(data, 0, this.data, 0, data.length);
		}
	}

	/**
	 * @param key
	 */
	private Key(Key key) {
		this.data = new byte[key.getData().length];
		System.arraycopy(key.getData(), 0, this.data, 0, this.data.length);
	}

	/**
	 * @param data
	 * @return
	 */
	public static Key create(byte[] data) {
		return new Key(data);
	}

	/**
	 * @return
	 */
	public Key clone() {
		return new Key(this);
	}

	/**
	 * @return
	 */
	public byte[] getData() {
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Key key = (Key) o;
		return Arrays.equals(key.getData(), this.data);
	}

	@Override
	public int hashCode() {
		return data != null ? ArrayUtils.hashCode(data) : 0;
	}
}
