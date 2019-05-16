package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.core.db.Manager;

public abstract class AbstractActuator implements Actuator {

	protected Any contract;
	protected Manager dbManager;
	protected Deposit deposit;

	AbstractActuator(Any contract, Manager dbManager) {
		this.contract = contract;
		this.dbManager = dbManager;
	}

	public Deposit getDeposit() {
		return deposit;
	}

	public void setDeposit(Deposit deposit) {
		this.deposit = deposit;
	}
}
