package io.midasprotocol.common.application;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import io.midasprotocol.common.overlay.discover.DiscoverServer;
import io.midasprotocol.common.overlay.discover.node.NodeManager;
import io.midasprotocol.common.overlay.server.ChannelManager;
import io.midasprotocol.core.db.Manager;

public class ApplicationContext extends AnnotationConfigApplicationContext {

	public ApplicationContext() {
	}

	public ApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
	}

	public ApplicationContext(Class<?>... annotatedClasses) {
		super(annotatedClasses);
	}

	public ApplicationContext(String... basePackages) {
		super(basePackages);
	}

	@Override
	public void destroy() {

		Application appT = ApplicationFactory.create(this);
		appT.shutdownServices();
		appT.shutdown();

		DiscoverServer discoverServer = getBean(DiscoverServer.class);
		discoverServer.close();
		ChannelManager channelManager = getBean(ChannelManager.class);
		channelManager.close();
		NodeManager nodeManager = getBean(NodeManager.class);
		nodeManager.close();

		Manager dbManager = getBean(Manager.class);
		dbManager.stopRepushThread();
		dbManager.stopRepushTriggerThread();
		super.destroy();
	}
}
