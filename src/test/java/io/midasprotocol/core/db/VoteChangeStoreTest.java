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
import io.midasprotocol.core.capsule.VoteChangeCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;

import java.io.File;

@Slf4j
public class VoteChangeStoreTest {

	private static final String dbPath = "output-voteChangeStore-test";
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
	}

	private VoteChangeStore voteChangeStore;

	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Before
	public void initDb() {
		this.voteChangeStore = context.getBean(VoteChangeStore.class);
	}

	@Test
	public void putAndGetVotes() {
		VoteChangeCapsule voteChangeCapsule = new VoteChangeCapsule(ByteString.copyFromUtf8("100000000x"),
			null);
		this.voteChangeStore.put(voteChangeCapsule.createDbKey(), voteChangeCapsule);

		Assert.assertTrue("voteChangeStore is empty", voteChangeStore.iterator().hasNext());
		Assert.assertTrue(voteChangeStore.has(voteChangeCapsule.createDbKey()));
		VoteChangeCapsule votesSource = this.voteChangeStore
			.get(ByteString.copyFromUtf8("100000000x").toByteArray());
		Assert.assertEquals(voteChangeCapsule.getAddress(), votesSource.getAddress());
		Assert.assertEquals(ByteString.copyFromUtf8("100000000x"), votesSource.getAddress());
	}
}