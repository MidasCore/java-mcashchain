package io.midasprotocol.program;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import io.midasprotocol.common.application.Application;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.services.RpcApiService;
import io.midasprotocol.core.services.WitnessService;
import io.midasprotocol.core.services.http.FullNodeHttpApiService;
import io.midasprotocol.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;
import io.midasprotocol.core.services.interfaceOnSolidity.http.solidity.HttpApiOnSolidityService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.File;

@Slf4j(topic = "app")
public class FullNode {

	public static void load(String path) {
		try {
			File file = new File(path);
			if (!file.exists() || !file.isFile() || !file.canRead()) {
				return;
			}
			LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(lc);
			lc.reset();
			configurator.doConfigure(file);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	/**
	 * Start the FullNode.
	 */
	public static void main(String[] args) {
		logger.info("Full node running.");
		Args.setParam(args, Constant.TESTNET_CONF);
		Args cfgArgs = Args.getInstance();

		load(cfgArgs.getLogbackPath());

		if (cfgArgs.isHelp()) {
			logger.info("Here is the help message.");
			return;
		}

		if (Args.getInstance().isDebug()) {
			logger.info("in debug mode, it won't check energy time");
		} else {
			logger.info("not in debug mode, it will check energy time");
		}

		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.setAllowCircularReferences(false);
		ApplicationContext context =
			new ApplicationContext(beanFactory);
		context.register(DefaultConfig.class);

		context.refresh();
		Application appT = ApplicationFactory.create(context);
		shutdown(appT);

		// grpc api server
		RpcApiService rpcApiService = context.getBean(RpcApiService.class);
		appT.addService(rpcApiService);
		if (cfgArgs.isWitness()) {
			appT.addService(new WitnessService(appT, context));
		}

		// http api server
		FullNodeHttpApiService httpApiService = context.getBean(FullNodeHttpApiService.class);
		appT.addService(httpApiService);

		// fullnode and soliditynode fuse together, provide solidity rpc and http server on the fullnode.
		if (Args.getInstance().getStorage().getDbVersion() == 2) {
			RpcApiServiceOnSolidity rpcApiServiceOnSolidity = context
				.getBean(RpcApiServiceOnSolidity.class);
			appT.addService(rpcApiServiceOnSolidity);
			HttpApiOnSolidityService httpApiOnSolidityService = context
				.getBean(HttpApiOnSolidityService.class);
			appT.addService(httpApiOnSolidityService);
		}

		appT.initServices(cfgArgs);
		appT.startServices();
		appT.startup();

		rpcApiService.blockUntilShutdown();
	}

	public static void shutdown(final Application app) {
		logger.info("********register application shutdown hook********");
		Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
	}
}
