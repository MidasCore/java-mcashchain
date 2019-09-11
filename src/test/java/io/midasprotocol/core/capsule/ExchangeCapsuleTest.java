package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ItemNotFoundException;
import io.midasprotocol.core.util.ConversionUtil;

import java.io.File;

@Slf4j
public class ExchangeCapsuleTest {

	private static final String dbPath = "output_exchange_capsule_test_test";
	private static final String OWNER_ADDRESS;
	private static final String OWNER_ADDRESS_INVALID = "aaaa";
	private static final String OWNER_ACCOUNT_INVALID;
	private static final long initBalance = ConversionUtil.McashToMatoshi(10_000_000_000L);
	private static Manager dbManager;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
		OWNER_ACCOUNT_INVALID = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
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
				1L,
				2L);

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
			long sellID = 1L;

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
