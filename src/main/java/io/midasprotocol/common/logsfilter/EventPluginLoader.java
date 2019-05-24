package io.midasprotocol.common.logsfilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginManager;
import org.springframework.util.StringUtils;
import io.midasprotocol.common.logsfilter.trigger.BlockLogTrigger;
import io.midasprotocol.common.logsfilter.trigger.ContractEventTrigger;
import io.midasprotocol.common.logsfilter.trigger.ContractLogTrigger;
import io.midasprotocol.common.logsfilter.trigger.TransactionLogTrigger;
import io.midasprotocol.common.logsfilter.trigger.Trigger;

@Slf4j
public class EventPluginLoader {

	private static EventPluginLoader instance;

	private PluginManager pluginManager = null;

	private List<IPluginEventListener> eventListeners;

	private ObjectMapper objectMapper = new ObjectMapper();

	private String serverAddress;

	private String dbConfig;

	private List<TriggerConfig> triggerConfigList;

	private boolean blockLogTriggerEnable = false;

	private boolean transactionLogTriggerEnable = false;

	private boolean contractEventTriggerEnable = false;

	private boolean contractLogTriggerEnable = false;

	private FilterQuery filterQuery;

	public static EventPluginLoader getInstance() {
		if (Objects.isNull(instance)) {
			synchronized (EventPluginLoader.class) {
				if (Objects.isNull(instance)) {
					instance = new EventPluginLoader();
				}
			}
		}
		return instance;
	}

	public boolean start(EventPluginConfig config) {
		boolean success = false;

		if (Objects.isNull(config)) {
			return success;
		}

		// parsing subscribe config from config.conf
		String pluginPath = config.getPluginPath();
		this.serverAddress = config.getServerAddress();
		this.triggerConfigList = config.getTriggerConfigList();
		this.dbConfig = config.getDbConfig();

		if (!startPlugin(pluginPath)) {
			logger.error("failed to load '{}'", pluginPath);
			return success;
		}

		setPluginConfig();

		if (Objects.nonNull(eventListeners)) {
			eventListeners.forEach(listener -> listener.start());
		}

		return true;
	}

	private void setPluginConfig() {

		if (Objects.isNull(eventListeners)) {
			return;
		}

		// set server address to plugin
		eventListeners.forEach(listener -> listener.setServerAddress(this.serverAddress));

		// set dbconfig to plugin
		eventListeners.forEach(listener -> listener.setDBConfig(this.dbConfig));

		triggerConfigList.forEach(triggerConfig -> {
			if (EventPluginConfig.BLOCK_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())) {
				if (triggerConfig.isEnabled()) {
					blockLogTriggerEnable = true;
				} else {
					blockLogTriggerEnable = false;
				}
				setPluginTopic(Trigger.BLOCK_TRIGGER, triggerConfig.getTopic());
			} else if (EventPluginConfig.TRANSACTION_TRIGGER_NAME
					.equalsIgnoreCase(triggerConfig.getTriggerName())) {
				if (triggerConfig.isEnabled()) {
					transactionLogTriggerEnable = true;
				} else {
					transactionLogTriggerEnable = false;
				}
				setPluginTopic(Trigger.TRANSACTION_TRIGGER, triggerConfig.getTopic());
			} else if (EventPluginConfig.CONTRACTEVENT_TRIGGER_NAME
					.equalsIgnoreCase(triggerConfig.getTriggerName())) {
				if (triggerConfig.isEnabled()) {
					contractEventTriggerEnable = true;
				} else {
					contractEventTriggerEnable = false;
				}
				setPluginTopic(Trigger.CONTRACTEVENT_TRIGGER, triggerConfig.getTopic());
			} else if (EventPluginConfig.CONTRACTLOG_TRIGGER_NAME
					.equalsIgnoreCase(triggerConfig.getTriggerName())) {
				if (triggerConfig.isEnabled()) {
					contractLogTriggerEnable = true;
				} else {
					contractLogTriggerEnable = false;
				}
				setPluginTopic(Trigger.CONTRACTLOG_TRIGGER, triggerConfig.getTopic());
			}
		});
	}

	public synchronized boolean isBlockLogTriggerEnable() {
		return blockLogTriggerEnable;
	}

	public synchronized boolean isTransactionLogTriggerEnable() {
		return transactionLogTriggerEnable;
	}

	public synchronized boolean isContractEventTriggerEnable() {
		return contractEventTriggerEnable;
	}

	public synchronized boolean isContractLogTriggerEnable() {
		return contractLogTriggerEnable;
	}

	private void setPluginTopic(int eventType, String topic) {
		eventListeners.forEach(listener -> listener.setTopic(eventType, topic));
	}

	private boolean startPlugin(String path) {
		boolean loaded = false;
		logger.info("start loading '{}'", path);

		File pluginPath = new File(path);
		if (!pluginPath.exists()) {
			logger.error("'{}' doesn't exist", path);
			return loaded;
		}

		if (Objects.isNull(pluginManager)) {

			pluginManager = new DefaultPluginManager(pluginPath.toPath()) {
				@Override
				protected CompoundPluginDescriptorFinder createPluginDescriptorFinder() {
					return new CompoundPluginDescriptorFinder()
							.add(new ManifestPluginDescriptorFinder());
				}
			};
		}

		String pluginId = pluginManager.loadPlugin(pluginPath.toPath());
		if (StringUtils.isEmpty(pluginId)) {
			logger.error("invalid pluginID");
			return loaded;
		}

		pluginManager.startPlugins();

		eventListeners = pluginManager.getExtensions(IPluginEventListener.class);

		if (Objects.isNull(eventListeners) || eventListeners.isEmpty()) {
			logger.error("No eventListener is registered");
			return loaded;
		}

		loaded = true;

		logger.info("'{}' loaded", path);

		return loaded;
	}

	public void stopPlugin() {
		if (Objects.isNull(pluginManager)) {
			logger.info("pluginManager is null");
			return;
		}

		pluginManager.stopPlugins();
		logger.info("eventPlugin stopped");
	}

	public void postBlockTrigger(BlockLogTrigger trigger) {
		eventListeners.forEach(listener ->
				listener.handleBlockEvent(toJsonString(trigger)));
	}

	public void postTransactionTrigger(TransactionLogTrigger trigger) {
		eventListeners.forEach(listener -> listener.handleTransactionTrigger(toJsonString(trigger)));
	}

	public void postContractLogTrigger(ContractLogTrigger trigger) {
		eventListeners.forEach(listener ->
				listener.handleContractLogTrigger(toJsonString(trigger)));
	}

	public void postContractEventTrigger(ContractEventTrigger trigger) {
		eventListeners.forEach(listener ->
				listener.handleContractEventTrigger(toJsonString(trigger)));
	}

	private String toJsonString(Object data) {
		String jsonData = "";

		try {
			jsonData = objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			logger.error("'{}'", e);
		}

		return jsonData;
	}

	public synchronized void setFilterQuery(FilterQuery filterQuery) {
		this.filterQuery = filterQuery;
	}

	public synchronized FilterQuery getFilterQuery() {
		return filterQuery;
	}


}
