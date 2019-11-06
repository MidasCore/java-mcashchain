package io.midasprotocol.core.db;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.TransactionInfoCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.BadItemException;

import java.io.File;

public class TransactionHistoryTest {

	private static final byte[] transactionId = TransactionStoreTest.randomBytes(32);
	private static String dbPath = "output_transaction_history_store_test";
	private static String dbDirectory = "db__transaction_history_store_test";
	private static String indexDirectory = "index_transaction_history_store_test";
	private static ApplicationContext context;
	private static TransactionHistoryStore transactionHistoryStore;

	static {
		Args.setParam(
			new String[]{
				"--output-directory", dbPath,
				"--storage-db-directory", dbDirectory,
				"--storage-index-directory", indexDirectory
			},
			Constant.TEST_CONF
		);
		context = new ApplicationContext(DefaultConfig.class);
	}

	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@BeforeClass
	public static void init() {
		transactionHistoryStore = context.getBean(TransactionHistoryStore.class);
		TransactionInfoCapsule transactionInfoCapsule = new TransactionInfoCapsule();

		transactionInfoCapsule.setId(transactionId);
		transactionInfoCapsule.setFee(1000L);
		transactionInfoCapsule.setBlockNumber(100L);
		transactionInfoCapsule.setBlockTimeStamp(200L);
		transactionHistoryStore.put(transactionId, transactionInfoCapsule);
	}

	@Test
	public void get() throws BadItemException {
		//test get and has Method
		TransactionInfoCapsule resultCapsule = transactionHistoryStore.get(transactionId);
		Assert.assertEquals(1000L, resultCapsule.getFee());
		Assert.assertEquals(100L, resultCapsule.getBlockNumber());
		Assert.assertEquals(200L, resultCapsule.getBlockTimeStamp());
		Assert.assertEquals(ByteArray.toHexString(transactionId),
			ByteArray.toHexString(resultCapsule.getId()));
	}
}