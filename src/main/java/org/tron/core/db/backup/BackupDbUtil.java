package org.tron.core.db.backup;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.PropUtil;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.RevokingDatabase;
import org.tron.core.db2.core.RevokingDBWithCachingNewValue;
import org.tron.core.db2.core.SnapshotManager;
import org.tron.core.db2.core.SnapshotRoot;

import java.util.List;

@Slf4j
@Component
public class BackupDbUtil {

	private static final int DB_BACKUP_INDEX1 = 1;
	private static final int DB_BACKUP_INDEX2 = 2;
	@Getter
	private static final int DB_BACKUP_STATE_DEFAULT = 11;
	@Getter
	private static String DB_BACKUP_STATE = "DB";
	@Getter
	@Autowired
	private RevokingDatabase db;
	private Args args = Args.getInstance();

	private int getBackupState() {
		try {
			return Integer.valueOf(PropUtil
					.readProperty(args.getDbBackupConfig().getPropPath(), BackupDbUtil.DB_BACKUP_STATE)
			);
		} catch (NumberFormatException ignore) {
			return DB_BACKUP_STATE_DEFAULT;  //get default state if prop file is newly created
		}
	}

	private void setBackupState(int status) {
		PropUtil.writeProperty(args.getDbBackupConfig().getPropPath(), BackupDbUtil.DB_BACKUP_STATE,
				String.valueOf(status));
	}

	private void switchBackupState() {
		switch (State.valueOf(getBackupState())) {
			case BAKINGONE:
				setBackupState(State.BAKEDONE.getStatus());
				break;
			case BAKEDONE:
				setBackupState(State.BAKEDTWO.getStatus());
				break;
			case BAKINGTWO:
				setBackupState(State.BAKEDTWO.getStatus());
				break;
			case BAKEDTWO:
				setBackupState(State.BAKEDONE.getStatus());
				break;
			default:
				break;
		}
	}

	public void doBackup(BlockCapsule block) {
		long t1 = System.currentTimeMillis();
		try {
			switch (State.valueOf(getBackupState())) {
				case BAKINGONE:
					deleteBackup(DB_BACKUP_INDEX1);
					backup(DB_BACKUP_INDEX1);
					switchBackupState();
					deleteBackup(DB_BACKUP_INDEX2);
					break;
				case BAKEDONE:
					deleteBackup(DB_BACKUP_INDEX2);
					backup(DB_BACKUP_INDEX2);
					switchBackupState();
					deleteBackup(DB_BACKUP_INDEX1);
					break;
				case BAKINGTWO:
					deleteBackup(DB_BACKUP_INDEX2);
					backup(DB_BACKUP_INDEX2);
					switchBackupState();
					deleteBackup(DB_BACKUP_INDEX1);
					break;
				case BAKEDTWO:
					deleteBackup(DB_BACKUP_INDEX1);
					backup(DB_BACKUP_INDEX1);
					switchBackupState();
					deleteBackup(DB_BACKUP_INDEX2);
					break;
				default:
					logger.warn("invalid backup state");
			}
		} catch (RocksDBException | SecurityException e) {
			logger.warn("backup db error:" + e);
		}
		long timeUsed = System.currentTimeMillis() - t1;
		logger
				.info("current block number is {}, backup all store use {} ms!", block.getNum(), timeUsed);
		if (timeUsed >= 3000) {
			logger.warn("backup db use too much time.");
		}
	}

	private void backup(int i) throws RocksDBException {
		String path = "";
		if (i == DB_BACKUP_INDEX1) {
			path = args.getDbBackupConfig().getBak1path();
		} else if (i == DB_BACKUP_INDEX2) {
			path = args.getDbBackupConfig().getBak2path();
		} else {
			throw new RuntimeException("Error backup with undefined index");
		}
		List<RevokingDBWithCachingNewValue> stores = ((SnapshotManager) db).getDbs();
		for (RevokingDBWithCachingNewValue store : stores) {
			if (((SnapshotRoot) (store.getHead().getRoot())).getDb().getClass()
					== org.tron.core.db2.common.RocksDB.class) {
				((org.tron.core.db2.common.RocksDB) ((SnapshotRoot) (store.getHead().getRoot())).getDb())
						.getDb().backup(path);
			}
		}
	}

	private void deleteBackup(int i) {
		String path = "";
		if (i == DB_BACKUP_INDEX1) {
			path = args.getDbBackupConfig().getBak1path();
		} else if (i == DB_BACKUP_INDEX2) {
			path = args.getDbBackupConfig().getBak2path();
		} else {
			throw new RuntimeException("Error deleteBackup with undefined index");
		}
		List<RevokingDBWithCachingNewValue> stores = ((SnapshotManager) db).getDbs();
		for (RevokingDBWithCachingNewValue store : stores) {
			if (((SnapshotRoot) (store.getHead().getRoot())).getDb().getClass()
					== org.tron.core.db2.common.RocksDB.class) {
				((org.tron.core.db2.common.RocksDB) (((SnapshotRoot) (store.getHead().getRoot())).getDb()))
						.getDb().deleteDbBakPath(path);
			}
		}
	}

	public enum State {
		BAKINGONE(1), BAKEDONE(11), BAKINGTWO(2), BAKEDTWO(22);
		public int status;

		private State(int status) {
			this.status = status;
		}

		public static State valueOf(int value) {
			switch (value) {
				case 1:
					return BAKINGONE;
				case 11:
					return BAKEDONE;
				case 2:
					return BAKINGTWO;
				case 22:
					return BAKEDTWO;
				default:
					return BAKEDONE;
			}
		}

		public int getStatus() {
			return status;
		}
	}
}
