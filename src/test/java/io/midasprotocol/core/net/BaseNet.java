package io.midasprotocol.core.net;

import io.midasprotocol.common.utils.ReflectUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.net.peer.PeerConnection;
import io.midasprotocol.core.services.RpcApiService;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class BaseNet {

	private static String dbPath = "output_net";
	private static String dbDirectory = "net_database";
	private static String indexDirectory = "net_index";
	private static int port = 10000;

	protected ApplicationContext context;

	private RpcApiService rpcApiService;
	private Application appT;
	private TronNetDelegate tronNetDelegate;

	private ExecutorService executorService = Executors.newFixedThreadPool(1);

	public static Channel connect(ByteToMessageDecoder decoder) throws InterruptedException {
		NioEventLoopGroup group = new NioEventLoopGroup(1);
		Bootstrap b = new Bootstrap();
		b.group(group).channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<Channel>() {
					@Override
					protected void initChannel(Channel ch) throws Exception {
						ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
						ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
						ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);
						ch.pipeline()
								.addLast("readTimeoutHandler",
										new ReadTimeoutHandler(600, TimeUnit.SECONDS))
								.addLast("writeTimeoutHandler",
										new WriteTimeoutHandler(600, TimeUnit.SECONDS));
						ch.pipeline().addLast("protoPender",
								new ProtobufVarint32LengthFieldPrepender());
						ch.pipeline().addLast("lengthDecode",
								new ProtobufVarint32FrameDecoder());
						ch.pipeline().addLast("handshakeHandler", decoder);
						ch.closeFuture();
					}
				}).option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)
				.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
		return b.connect("127.0.0.1", port).sync().channel();
	}

	@Before
	public void init() throws Exception {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				logger.info("Full node running.");
				Args.setParam(
						new String[]{
								"--output-directory", dbPath,
								"--storage-db-directory", dbDirectory,
								"--storage-index-directory", indexDirectory
						},
						"config.conf"
				);
				Args cfgArgs = Args.getInstance();
				cfgArgs.setNodeListenPort(port);
				cfgArgs.getSeedNode().getIpList().clear();
				cfgArgs.setNodeExternalIp("127.0.0.1");
				context = new ApplicationContext(DefaultConfig.class);
				appT = ApplicationFactory.create(context);
				rpcApiService = context.getBean(RpcApiService.class);
				appT.addService(rpcApiService);
				appT.initServices(cfgArgs);
				appT.startServices();
				appT.startup();
				tronNetDelegate = context.getBean(TronNetDelegate.class);
				rpcApiService.blockUntilShutdown();
			}
		});
		int tryTimes = 0;
		while (++tryTimes < 100 && tronNetDelegate == null) {
			Thread.sleep(3000);
		}
	}

	@After
	public void destroy() {
		Collection<PeerConnection> peerConnections = ReflectUtils
				.invokeMethod(tronNetDelegate, "getActivePeer");
		for (PeerConnection peer : peerConnections) {
			peer.close();
		}
		context.destroy();
		FileUtil.deleteDir(new File(dbPath));
	}
}
