package io.midasprotocol.core.db;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.BlockCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.BadNumberBlockException;
import io.midasprotocol.core.exception.UnLinkedBlockException;
import io.midasprotocol.protos.Protocol.Block;
import io.midasprotocol.protos.Protocol.BlockHeader;
import io.midasprotocol.protos.Protocol.BlockHeader.Raw;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

@Slf4j
public class KhaosDatabaseTest {

	private static final String dbPath = "output_khaos_database_test";
	private static KhaosDatabase khaosDatabase;
	private static ApplicationContext context;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath},
			Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
	}

	@BeforeClass
	public static void init() {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		khaosDatabase = context.getBean(KhaosDatabase.class);
	}

	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Test
	public void testStartBlock() {
		BlockCapsule blockCapsule = new BlockCapsule(Block.newBuilder().setBlockHeader(
			BlockHeader.newBuilder().setRawData(Raw.newBuilder().setParentHash(ByteString.copyFrom(
				ByteArray.fromHexString(
					"0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b81")))
			)).build());
		khaosDatabase.start(blockCapsule);

		Assert.assertEquals(blockCapsule, khaosDatabase.getBlock(blockCapsule.getBlockId()));
	}

	@Test
	public void testPushGetBlock() {
		BlockCapsule blockCapsule = new BlockCapsule(Block.newBuilder().setBlockHeader(
			BlockHeader.newBuilder().setRawData(Raw.newBuilder().setParentHash(ByteString.copyFrom(
				ByteArray.fromHexString(
					"0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b81")))
			)).build());
		BlockCapsule blockCapsule2 = new BlockCapsule(Block.newBuilder().setBlockHeader(
			BlockHeader.newBuilder().setRawData(Raw.newBuilder().setParentHash(ByteString.copyFrom(
				ByteArray.fromHexString(
					"9938a342238077182498b464ac029222ae169360e540d1fd6aee7c2ae9575a06")))
			)).build());
		khaosDatabase.start(blockCapsule);
		try {
			khaosDatabase.push(blockCapsule2);
		} catch (UnLinkedBlockException | BadNumberBlockException e) {

		}

		Assert.assertEquals(blockCapsule2, khaosDatabase.getBlock(blockCapsule2.getBlockId()));
		Assert.assertTrue("contain is error", khaosDatabase.containBlock(blockCapsule2.getBlockId()));

		khaosDatabase.removeBlk(blockCapsule2.getBlockId());

		Assert.assertNull("removeBlk is error", khaosDatabase.getBlock(blockCapsule2.getBlockId()));
	}


	@Test
	@Ignore
	public void checkWeakReference() throws UnLinkedBlockException, BadNumberBlockException {
		BlockCapsule blockCapsule = new BlockCapsule(Block.newBuilder().setBlockHeader(
			BlockHeader.newBuilder().setRawData(Raw.newBuilder().setParentHash(ByteString.copyFrom(
				ByteArray
					.fromHexString("0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b82")))
				.setNumber(0)
			)).build());
		BlockCapsule blockCapsule2 = new BlockCapsule(Block.newBuilder().setBlockHeader(
			BlockHeader.newBuilder().setRawData(Raw.newBuilder().setParentHash(ByteString.copyFrom(
				blockCapsule.getBlockId().getBytes())).setNumber(1))).build());
		Assert.assertEquals(blockCapsule.getBlockId(), blockCapsule2.getParentHash());

		khaosDatabase.start(blockCapsule);
		khaosDatabase.push(blockCapsule2);

		khaosDatabase.removeBlk(blockCapsule.getBlockId());
		logger.info("*** " + khaosDatabase.getBlock(blockCapsule.getBlockId()));
		Object object = new Object();
		Reference<Object> objectReference = new WeakReference<>(object);
		System.gc();
		logger.info("***** object ref:" + objectReference.get());
		Assert.assertNull(objectReference.get());
		Assert.assertNull(khaosDatabase.getParentBlock(blockCapsule2.getBlockId()));
	}
}