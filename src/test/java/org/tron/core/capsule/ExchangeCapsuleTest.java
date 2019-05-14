package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.db.StorageMarket;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.util.ConversionUtil;

import java.io.File;

@Slf4j
public class ExchangeCapsuleTest {

	private static final String dbPath = "output_exchange_capsule_test_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(10_000_000_000L);
	private static Manager dbManager;
	private static StorageMarket storageMarket;
	private static TronApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = "548794500882809695a8a687866e76d4271a1abc";
		OWNER_ACCOUNT_INVALID = "548794500882809695a8a687866e76d4271a3456";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		storageMarket = new StorageMarket(dbManager);
		//    Args.setParam(new String[]{"--output-directory", dbPath},
		//        "config-junit.conf");
		//    dbManager = new Manager();
		//    dbManager.init();
	}

	/**
	 * Release resources.
	 */
	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	/**
	 * create temp Capsule test need.
	 */
	@Before
	public void createExchangeCapsule() {
		dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);

		long now = dbManager.getHeadBlockTimeStamp();
		ExchangeCapsule exchangeCapsulee =
				new ExchangeCapsule(
						ByteString.copyFromUtf8("owner"),
						1,
						now,
						"abc".getBytes(),
						"def".getBytes());

		dbManager.getExchangeStore().put(exchangeCapsulee.createDbKey(), exchangeCapsulee);

	}

	@Test
	public void testExchange() {
		long sellBalance = ConversionUtil.McashToMatoshi(100);
		long buyBalance = ConversionUtil.McashToMatoshi(100);

		byte[] key = ByteArray.fromLong(1);

		ExchangeCapsule exchangeCapsule;
		try {
			exchangeCapsule = dbManager.getExchangeStore().get(key);
			exchangeCapsule.setBalance(sellBalance, buyBalance);

			long sellQuant = ConversionUtil.McashToMatoshi(1);
			byte[] sellID = "abc".getBytes();

			long result = exchangeCapsule.transaction(sellID, sellQuant);
			Assert.assertEquals(990_09900L, result);
			sellBalance += sellQuant;
			Assert.assertEquals(sellBalance, exchangeCapsule.getFirstTokenBalance());
			buyBalance -= result;
			Assert.assertEquals(buyBalance, exchangeCapsule.getSecondTokenBalance());

			sellQuant = ConversionUtil.McashToMatoshi(1);
			long result2 = exchangeCapsule.transaction(sellID, sellQuant);
			Assert.assertEquals(196078430, result + result2);
			sellBalance += sellQuant;
			Assert.assertEquals(sellBalance, exchangeCapsule.getFirstTokenBalance());
			buyBalance -= result2;
			Assert.assertEquals(buyBalance, exchangeCapsule.getSecondTokenBalance());

		} catch (ItemNotFoundException e) {
			Assert.fail();
		}

	}


}
