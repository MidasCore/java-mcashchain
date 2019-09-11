package io.midasprotocol.core.db;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;

import java.io.File;

@Slf4j
public class WitnessStoreTest {

	private static final String dbPath = "output-witnessStore-test";
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
	}

	WitnessStore witnessStore;

	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Before
	public void initDb() {
		this.witnessStore = context.getBean(WitnessStore.class);
	}

	@Test
	public void putAndGetWitness() {
		WitnessCapsule witnessCapsule = new WitnessCapsule(
			ByteString.copyFromUtf8("100000000x"),
			ByteString.copyFromUtf8("100000000x"),
			100L, "");

		this.witnessStore.put(witnessCapsule.getAddress().toByteArray(), witnessCapsule);
		WitnessCapsule witnessSource = this.witnessStore
			.get(ByteString.copyFromUtf8("100000000x").toByteArray());
		Assert.assertEquals(witnessCapsule.getAddress(), witnessSource.getAddress());
		Assert.assertEquals(witnessCapsule.getVoteCount(), witnessSource.getVoteCount());

		Assert.assertEquals(ByteString.copyFromUtf8("100000000x"), witnessSource.getAddress());
		Assert.assertEquals(100L, witnessSource.getVoteCount());

		witnessCapsule = new WitnessCapsule(
			ByteString.copyFromUtf8(""),
			ByteString.copyFromUtf8(""),
			100L, "");

		this.witnessStore.put(witnessCapsule.getAddress().toByteArray(), witnessCapsule);
		witnessSource = this.witnessStore.get(ByteString.copyFromUtf8("").toByteArray());
		Assert.assertEquals(witnessCapsule.getAddress(), witnessSource.getAddress());
		Assert.assertEquals(witnessCapsule.getVoteCount(), witnessSource.getVoteCount());

		Assert.assertEquals(ByteString.copyFromUtf8(""), witnessSource.getAddress());
		Assert.assertEquals(100L, witnessSource.getVoteCount());
	}


}