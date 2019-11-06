package io.midasprotocol.core.db;

import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;

public class AccountStoreTest {

	private static final byte[] data = TransactionStoreTest.randomBytes(32);
	private static String dbPath = "output_account_store_test";
	private static String dbDirectory = "db_account_store_test";
	private static String indexDirectory = "index_account_store_test";
	private static ApplicationContext context;
	private static AccountStore accountStore;
	private static byte[] address = TransactionStoreTest.randomBytes(32);
	private static byte[] accountName = TransactionStoreTest.randomBytes(32);

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
		accountStore = context.getBean(AccountStore.class);
		AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom(address),
			ByteString.copyFrom(accountName),
			AccountType.forNumber(1));
		accountStore.put(data, accountCapsule);
	}

	@Test
	public void get() {
		//test get and has Method
		Assert.assertEquals(ByteArray.toHexString(address), ByteArray
			.toHexString(accountStore.get(data).getInstance().getAddress().toByteArray()));
		Assert.assertEquals(ByteArray.toHexString(accountName), ByteArray
			.toHexString(accountStore.get(data).getInstance().getAccountName().toByteArray()));
		Assert.assertTrue(accountStore.has(data));
	}
}