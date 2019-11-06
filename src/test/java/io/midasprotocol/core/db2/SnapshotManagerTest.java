package io.midasprotocol.core.db2;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.CheckTmpStore;
import io.midasprotocol.core.db2.RevokingDbWithCacheNewValueTest.TestRevokingTronStore;
import io.midasprotocol.core.db2.RevokingDbWithCacheNewValueTest.TestSnapshotManager;
import io.midasprotocol.core.db2.SnapshotRootTest.ProtoCapsuleTest;
import io.midasprotocol.core.db2.core.ISession;
import io.midasprotocol.core.db2.core.SnapshotManager;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.core.exception.ItemNotFoundException;

import java.io.File;

@Slf4j
public class SnapshotManagerTest {
	private static String dbPath = "output_revoking_store_test";

	private SnapshotManager revokingDatabase;
	private ApplicationContext context;
	private Application appT;
	private TestRevokingTronStore tronDatabase;

	@Before
	public void init() {
		Args.setParam(new String[]{"-d", dbPath},
			Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		appT = ApplicationFactory.create(context);
		revokingDatabase = new TestSnapshotManager();
		revokingDatabase.enable();
		tronDatabase = new TestRevokingTronStore("testSnapshotManager-test");
		revokingDatabase.add(tronDatabase.getRevokingDB());
		revokingDatabase.setCheckTmpStore(context.getBean(CheckTmpStore.class));
	}

	@After
	public void removeDb() {
		Args.clearParam();
		appT.shutdownServices();
		appT.shutdown();
		context.destroy();
		tronDatabase.close();
		FileUtil.deleteDir(new File(dbPath));
		revokingDatabase.getCheckTmpStore().getDbSource().closeDB();
		tronDatabase.close();
	}

	@Test
	public synchronized void testRefresh()
		throws BadItemException, ItemNotFoundException {
		while (revokingDatabase.size() != 0) {
			revokingDatabase.pop();
		}

		revokingDatabase.setMaxFlushCount(0);
		revokingDatabase.setUnChecked(false);
		revokingDatabase.setMaxSize(5);
		ProtoCapsuleTest protoCapsule = new ProtoCapsuleTest("refresh".getBytes());
		for (int i = 1; i < 11; i++) {
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("refresh" + i).getBytes());
			try (ISession tmpSession = revokingDatabase.buildSession()) {
				tronDatabase.put(protoCapsule.getData(), testProtoCapsule);
				tmpSession.commit();
			}
		}

		revokingDatabase.flush();
		Assert.assertEquals(new ProtoCapsuleTest("refresh10".getBytes()),
			tronDatabase.get(protoCapsule.getData()));
	}

	@Test
	public synchronized void testClose() {
		while (revokingDatabase.size() != 0) {
			revokingDatabase.pop();
		}

		revokingDatabase.setMaxFlushCount(0);
		revokingDatabase.setUnChecked(false);
		revokingDatabase.setMaxSize(5);
		ProtoCapsuleTest protoCapsule = new ProtoCapsuleTest("close".getBytes());
		for (int i = 1; i < 11; i++) {
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("close" + i).getBytes());
			try (ISession ignored = revokingDatabase.buildSession()) {
				tronDatabase.put(protoCapsule.getData(), testProtoCapsule);
			}
		}
		Assert.assertNull(tronDatabase.get(protoCapsule.getData()));

	}
}
