package io.midasprotocol.core.db2;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
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
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.AbstractRevokingStore;
import io.midasprotocol.core.db.RevokingDatabase;
import io.midasprotocol.core.db.TronStoreWithRevoking;
import io.midasprotocol.core.db2.SnapshotRootTest.ProtoCapsuleTest;
import io.midasprotocol.core.db2.core.ISession;
import io.midasprotocol.core.exception.RevokingStoreIllegalStateException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RevokingDbWithCacheOldValueTest {
	private static String dbPath = "output_revoking_store_test";

	private AbstractRevokingStore revokingDatabase;
	private ApplicationContext context;
	private Application appT;

	@Before
	public void init() {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		appT = ApplicationFactory.create(context);
		revokingDatabase = new TestRevokingTronDatabase();
		revokingDatabase.enable();
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
	public synchronized void testReset() {
		revokingDatabase.getStack().clear();
		TestRevokingTronStore tronDatabase = new TestRevokingTronStore(
			"testrevokingtronstore-testReset", revokingDatabase);
		ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("reset").getBytes());
		try (ISession tmpSession = revokingDatabase.buildSession()) {
			tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
			tmpSession.commit();
		}
		Assert.assertTrue(tronDatabase.has(testProtoCapsule.getData()));
		tronDatabase.reset();
		Assert.assertFalse(tronDatabase.has(testProtoCapsule.getData()));
		tronDatabase.reset();
	}

	@Test
	public synchronized void testPop() throws RevokingStoreIllegalStateException {
		revokingDatabase.getStack().clear();
		TestRevokingTronStore tronDatabase = new TestRevokingTronStore(
			"testrevokingtronstore-testPop", revokingDatabase);

		for (int i = 1; i < 11; i++) {
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("pop" + i).getBytes());
			try (ISession tmpSession = revokingDatabase.buildSession()) {
				tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
				Assert.assertEquals(1, revokingDatabase.getActiveDialog());
				tmpSession.commit();
				Assert.assertEquals(i, revokingDatabase.getStack().size());
				Assert.assertEquals(0, revokingDatabase.getActiveDialog());
			}
		}

		for (int i = 1; i < 11; i++) {
			revokingDatabase.pop();
			Assert.assertEquals(10 - i, revokingDatabase.getStack().size());
		}

		tronDatabase.close();

		Assert.assertEquals(0, revokingDatabase.getStack().size());
	}

	@Test
	public synchronized void testUndo() throws RevokingStoreIllegalStateException {
		revokingDatabase.getStack().clear();
		TestRevokingTronStore tronDatabase = new TestRevokingTronStore(
			"testrevokingtronstore-testUndo", revokingDatabase);

		SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
		for (int i = 0; i < 10; i++) {
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("undo" + i).getBytes());
			try (ISession tmpSession = revokingDatabase.buildSession()) {
				tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
				Assert.assertEquals(2, revokingDatabase.getStack().size());
				tmpSession.merge();
				Assert.assertEquals(1, revokingDatabase.getStack().size());
			}
		}

		Assert.assertEquals(1, revokingDatabase.getStack().size());

		dialog.reset();

		Assert.assertTrue(revokingDatabase.getStack().isEmpty());
		Assert.assertEquals(0, revokingDatabase.getActiveDialog());

		dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
		revokingDatabase.disable();
		ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("del".getBytes());
		tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
		revokingDatabase.enable();

		try (ISession tmpSession = revokingDatabase.buildSession()) {
			tronDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("del2".getBytes()));
			tmpSession.merge();
		}

		try (ISession tmpSession = revokingDatabase.buildSession()) {
			tronDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("del22".getBytes()));
			tmpSession.merge();
		}

		try (ISession tmpSession = revokingDatabase.buildSession()) {
			tronDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("del222".getBytes()));
			tmpSession.merge();
		}

		try (ISession tmpSession = revokingDatabase.buildSession()) {
			tronDatabase.delete(testProtoCapsule.getData());
			tmpSession.merge();
		}

		dialog.reset();

		logger.info("**********testProtoCapsule:" + tronDatabase.getUnchecked(testProtoCapsule.getData()));
		Assert.assertArrayEquals("del".getBytes(),
			tronDatabase.getUnchecked(testProtoCapsule.getData()).getData());
		Assert.assertEquals(testProtoCapsule, tronDatabase.getUnchecked(testProtoCapsule.getData()));

		tronDatabase.close();
	}

	@Test
	public synchronized void testGetlatestValues() {
		revokingDatabase.getStack().clear();
		TestRevokingTronStore tronDatabase = new TestRevokingTronStore(
			"testrevokingtronstore-testGetlatestValues", revokingDatabase);

		for (int i = 0; i < 10; i++) {
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getLastestValues" + i).getBytes());
			try (ISession tmpSession = revokingDatabase.buildSession()) {
				tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
				tmpSession.commit();
			}
		}
		Set<ProtoCapsuleTest> result = tronDatabase.getRevokingDB().getlatestValues(5).stream()
			.map(ProtoCapsuleTest::new)
			.collect(Collectors.toSet());

		for (int i = 9; i >= 5; i--) {
			Assert.assertTrue(result.contains(new ProtoCapsuleTest(("getLastestValues" + i).getBytes())));
		}
		tronDatabase.close();
	}

	@Test
	public synchronized void testGetValuesNext() {
		revokingDatabase.getStack().clear();
		TestRevokingTronStore tronDatabase = new TestRevokingTronStore(
			"testrevokingtronstore-testGetValuesNext", revokingDatabase);

		for (int i = 0; i < 10; i++) {
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getValuesNext" + i).getBytes());
			try (ISession tmpSession = revokingDatabase.buildSession()) {
				tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
				tmpSession.commit();
			}
		}
		Set<ProtoCapsuleTest> result =
			tronDatabase.getRevokingDB().getValuesNext(
				new ProtoCapsuleTest("getValuesNext2".getBytes()).getData(), 3)
				.stream()
				.map(ProtoCapsuleTest::new)
				.collect(Collectors.toSet());

		for (int i = 2; i < 5; i++) {
			Assert.assertEquals(true,
				result.contains(new ProtoCapsuleTest(("getValuesNext" + i).getBytes())));
		}
		tronDatabase.close();
	}

	@Test
	public void shutdown() throws RevokingStoreIllegalStateException {
		revokingDatabase.getStack().clear();
		TestRevokingTronStore tronDatabase = new TestRevokingTronStore(
			"testrevokingtronstore-shutdown", revokingDatabase);

		List<ProtoCapsuleTest> capsules = new ArrayList<>();
		for (int i = 1; i < 11; i++) {
			revokingDatabase.buildSession();
			ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("test" + i).getBytes());
			capsules.add(testProtoCapsule);
			tronDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
			Assert.assertEquals(revokingDatabase.getActiveDialog(), i);
			Assert.assertEquals(revokingDatabase.getStack().size(), i);
		}

		for (ProtoCapsuleTest capsule : capsules) {
			logger.info(new String(capsule.getData()));
			Assert.assertEquals(capsule, tronDatabase.getUnchecked(capsule.getData()));
		}

		revokingDatabase.shutdown();

		for (ProtoCapsuleTest capsule : capsules) {
			logger.info(tronDatabase.getUnchecked(capsule.getData()).toString());
			Assert.assertNull(tronDatabase.getUnchecked(capsule.getData()).getData());
		}

		Assert.assertEquals(0, revokingDatabase.getStack().size());
		tronDatabase.close();

	}

	private static class TestRevokingTronStore extends TronStoreWithRevoking<ProtoCapsuleTest> {

		protected TestRevokingTronStore(String dbName, RevokingDatabase revokingDatabase) {
			super(dbName, revokingDatabase);
		}

		@Override
		public ProtoCapsuleTest get(byte[] key) {
			byte[] value = this.revokingDB.getUnchecked(key);
			return ArrayUtils.isEmpty(value) ? null : new ProtoCapsuleTest(value);
		}
	}

	private static class TestRevokingTronDatabase extends AbstractRevokingStore {

	}
}
