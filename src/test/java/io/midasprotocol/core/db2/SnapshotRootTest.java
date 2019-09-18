package io.midasprotocol.core.db2;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.SessionOptional;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.ProtoCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db2.RevokingDbWithCacheNewValueTest.TestRevokingTronStore;
import io.midasprotocol.core.db2.RevokingDbWithCacheNewValueTest.TestSnapshotManager;
import io.midasprotocol.core.db2.core.ISession;
import io.midasprotocol.core.db2.core.Snapshot;
import io.midasprotocol.core.db2.core.SnapshotManager;
import io.midasprotocol.core.db2.core.SnapshotRoot;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SnapshotRootTest {
	private static String dbPath = "output_revoking_store_test";

	private TestRevokingTronStore tronDatabase;
	private ApplicationContext context;
	private Application appT;
	private SnapshotManager revokingDatabase;

	@Before
	public void init() {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		appT = ApplicationFactory.create(context);
	}

	@After
	public void removeDb() {
		Args.clearParam();
		appT.shutdownServices();
		appT.shutdown();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Test
	public synchronized void testRemove() {
		ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("test".getBytes());
		tronDatabase = new TestRevokingTronStore("testSnapshotRoot-testRemove");
		tronDatabase.put("test".getBytes(), testProtoCapsule);
		Assert.assertEquals(testProtoCapsule, tronDatabase.get("test".getBytes()));

		tronDatabase.delete("test".getBytes());
		Assert.assertNull(tronDatabase.get("test".getBytes()));
		tronDatabase.close();
	}

	@Test
	public synchronized void testMerge() {
		tronDatabase = new TestRevokingTronStore("testSnapshotRoot-testMerge");
		revokingDatabase = new TestSnapshotManager();
		revokingDatabase.enable();
		revokingDatabase.add(tronDatabase.getRevokingDB());

		SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
		ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("merge".getBytes());
		tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
		revokingDatabase.getDbs().forEach(db -> db.getHead().getRoot().merge(db.getHead()));
		dialog.reset();
		Assert.assertEquals(tronDatabase.get(testProtoCapsule.getData()), testProtoCapsule);

		tronDatabase.close();
	}

	@Test
	public synchronized void testMergeList() {
		tronDatabase = new TestRevokingTronStore("testSnapshotRoot-testMergeList");
		revokingDatabase = new TestSnapshotManager();
		revokingDatabase.enable();
		revokingDatabase.add(tronDatabase.getRevokingDB());

		SessionOptional.instance().setValue(revokingDatabase.buildSession());
		ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("test".getBytes());
		tronDatabase.put("merge".getBytes(), testProtoCapsule);
		for (int i = 1; i < 11; i++) {
			ProtoCapsuleTest tmpProtoCapsule = new ProtoCapsuleTest(("mergeList" + i).getBytes());
			try (ISession tmpSession = revokingDatabase.buildSession()) {
				tronDatabase.put(tmpProtoCapsule.getData(), tmpProtoCapsule);
				tmpSession.commit();
			}
		}
		revokingDatabase.getDbs().forEach(db -> {
			List<Snapshot> snapshots = new ArrayList<>();
			SnapshotRoot root = (SnapshotRoot) db.getHead().getRoot();
			Snapshot next = root;
			for (int i = 0; i < 11; ++i) {
				next = next.getNext();
				snapshots.add(next);
			}
			root.merge(snapshots);
			root.resetSolidity();

			for (int i = 1; i < 11; i++) {
				ProtoCapsuleTest tmpProtoCapsule = new ProtoCapsuleTest(("mergeList" + i).getBytes());
				Assert.assertEquals(tmpProtoCapsule, tronDatabase.get(tmpProtoCapsule.getData()));
			}

		});
		revokingDatabase.updateSolidity(10);
		tronDatabase.close();
	}

	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class ProtoCapsuleTest implements ProtoCapsule<Object> {

		private byte[] value;

		@Override
		public byte[] getData() {
			return value;
		}

		@Override
		public Object getInstance() {
			return value;
		}

		@Override
		public String toString() {
			return "ProtoCapsuleTest{"
				+ "value=" + Arrays.toString(value)
				+ ", string=" + (value == null ? "" : new String(value))
				+ '}';
		}
	}
}
