package io.midasprotocol.core.db;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;

import java.io.File;

@Slf4j
public class BlockStoreTest {

	private static final String dbPath = "output_blockStore_test";
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath},
			Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
	}

	private BlockStore blockStore;

	@Before
	public void init() {
		blockStore = context.getBean(BlockStore.class);
	}

	@After
	public void destroy() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Test
	public void testCreateBlockStore() {
	}
}
