package io.midasprotocol.core.db;

import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.db.TransactionTrace.TimeResultType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j(topic = "DB")
public class PendingManager implements AutoCloseable {

	@Getter
	static List<TransactionCapsule> tmpTransactions = new ArrayList<>();
	Manager dbManager;

	public PendingManager(Manager db) {

		this.dbManager = db;
		tmpTransactions.addAll(db.getPendingTransactions());
		db.getPendingTransactions().clear();
		db.getSession().reset();
	}

	@Override
	public void close() {

		for (TransactionCapsule tx : PendingManager.tmpTransactions) {
			try {
				if (tx.getTrxTrace() != null &&
					tx.getTrxTrace().getTimeResultType().equals(TimeResultType.NORMAL)) {
					dbManager.getRepushTransactions().put(tx);
				}
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
		tmpTransactions.clear();

		for (TransactionCapsule tx : dbManager.getPoppedTransactions()) {
			try {
				if (tx.getTrxTrace() != null &&
					tx.getTrxTrace().getTimeResultType().equals(TimeResultType.NORMAL)) {
					dbManager.getRepushTransactions().put(tx);
				}
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
		dbManager.getPoppedTransactions().clear();
	}
}
