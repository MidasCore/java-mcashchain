package org.tron.core.db.backup;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.RocksDB;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.PropUtil;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.db.ManagerForTest;
import org.tron.core.db2.core.RevokingDBWithCachingNewValue;
import org.tron.core.db2.core.SnapshotManager;

import java.io.File;
import java.util.List;

@Slf4j
public class BackupDbUtilTest {

	static {
		RocksDB.loadLibrary();
	}

	public TronApplicationContext context;
	public Application AppT = null;
	public BackupDbUtil dbBackupUtil;
	public Manager dbManager;
	public ManagerForTest mngForTest;
	public String dbPath = "output_backup_db_util_test";

	String propPath;
	String bak1Path;
	String bak2Path;
	int frequency;

	@Before
	public void before() {
		Args.setParam(
				new String[]{
						"--output-directory", dbPath,
						"--storage-db-directory", "database",
						"--storage-index-directory", "index"
				},
				"config-test-dbbackup.conf"
		);

		context = new TronApplicationContext(DefaultConfig.class);
		AppT = ApplicationFactory.create(context);
		dbManager = context.getBean(Manager.class);
		dbBackupUtil = context.getBean(BackupDbUtil.class);
		mngForTest = new ManagerForTest(dbManager);

		//prepare prop.properties
		propPath = dbPath + File.separator + "test_prop.properties";
		bak1Path = dbPath + File.separator + "bak1/database";
		bak2Path = dbPath + File.separator + "bak2/database";
		frequency = 50;
		Args cfgArgs = Args.getInstance();
		cfgArgs.getDbBackupConfig()
				.initArgs(true, propPath, bak1Path, bak2Path, frequency);
		FileUtil.createFileIfNotExists(propPath);
	}

	@After
	public void after() {
		AppT.shutdownServices();
		AppT.shutdown();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	@Test
	public void testDoBackup() {
		PropUtil.writeProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE(),
				String.valueOf("11"));
		mngForTest.pushNTestBlock(50);
		List<RevokingDBWithCachingNewValue> alist = ((SnapshotManager) dbBackupUtil.getDb()).getDbs();

		Assert.assertEquals(50, dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
		Assert.assertEquals("22", PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE()));

		mngForTest.pushNTestBlock(50);
		Assert.assertEquals(100, dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
		Assert.assertEquals("11", PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE()));

		mngForTest.pushNTestBlock(50);
		Assert.assertEquals(150, dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
		Assert.assertEquals("22", PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE()));

		PropUtil.writeProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE(), "1");
		mngForTest.pushNTestBlock(50);
		Assert.assertEquals(200, dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
		Assert.assertEquals("11", PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE()));

		PropUtil.writeProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE(), "2");
		mngForTest.pushNTestBlock(50);
		Assert.assertEquals(250, dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
		Assert.assertEquals("22", PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE()));
	}
}