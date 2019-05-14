package org.tron.core.db;

import com.google.protobuf.ByteString;

import java.io.File;

import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.VoteChangeCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;

@Slf4j
public class VoteChangeStoreTest {

	private static final String dbPath = "output-voteChangeStore-test";
	private static TronApplicationContext context;
	private VoteChangeStore voteChangeStore;

	static {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		context = new TronApplicationContext(DefaultConfig.class);
	}

	@Before
	public void initDb() {
		this.voteChangeStore = context.getBean(VoteChangeStore.class);
	}

	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
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