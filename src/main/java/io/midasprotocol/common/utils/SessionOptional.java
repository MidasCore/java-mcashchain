package io.midasprotocol.common.utils;

import io.midasprotocol.core.db2.core.ISession;

import java.util.Optional;

public final class SessionOptional {

	private static final SessionOptional INSTANCE = OptionalEnum.INSTANCE.getInstance();

	private Optional<ISession> value;

	private SessionOptional() {
		this.value = Optional.empty();
	}

	public static SessionOptional instance() {
		return INSTANCE;
	}

	public synchronized SessionOptional setValue(ISession value) {
		if (!this.value.isPresent()) {
			this.value = Optional.of(value);
		}
		return this;
	}

	public synchronized boolean valid() {
		return value.isPresent();
	}

	public synchronized void reset() {
		value.ifPresent(ISession::destroy);
		value = Optional.empty();
	}

	private enum OptionalEnum {
		INSTANCE;

		private SessionOptional instance;

		OptionalEnum() {
			instance = new SessionOptional();
		}

		private SessionOptional getInstance() {
			return instance;
		}
	}

}
