package io.midasprotocol.core.config;

import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.RevokingDatabase;
import io.midasprotocol.core.db.RevokingStore;
import io.midasprotocol.core.db.TransactionCache;
import io.midasprotocol.core.db.api.IndexHelper;
import io.midasprotocol.core.db.backup.BackupRocksDBAspect;
import io.midasprotocol.core.db.backup.NeedBeanCondition;
import io.midasprotocol.core.db2.core.SnapshotManager;
import io.midasprotocol.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;
import io.midasprotocol.core.services.interfaceOnSolidity.http.solidity.HttpApiOnSolidityService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.rocksdb.RocksDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j(topic = "app")
@Configuration
@Import(CommonConfig.class)
public class DefaultConfig {

	static {
		RocksDB.loadLibrary();
	}

	@Autowired
	ApplicationContext appCtx;

	@Autowired
	CommonConfig commonConfig;

	public DefaultConfig() {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
	}

	@Bean
	public IndexHelper indexHelper() {
		if (Args.getInstance().isSolidityNode()
			&& BooleanUtils.toBoolean(Args.getInstance().getStorage().getIndexSwitch())) {
			return new IndexHelper();
		}
		return null;
	}

	@Bean
	public RevokingDatabase revokingDatabase() {
		int dbVersion = Args.getInstance().getStorage().getDbVersion();
		RevokingDatabase revokingDatabase;
		try {
			if (dbVersion == 1) {
				revokingDatabase = RevokingStore.getInstance();
			} else if (dbVersion == 2) {
				revokingDatabase = new SnapshotManager();
			} else {
				throw new RuntimeException("db version is error.");
			}
			return revokingDatabase;
		} finally {
			logger.info("key-value data source created.");
		}
	}


	@Bean
	public RpcApiServiceOnSolidity getRpcApiServiceOnSolidity() {
		boolean isSolidityNode = Args.getInstance().isSolidityNode();
		int dbVersion = Args.getInstance().getStorage().getDbVersion();
		if (!isSolidityNode && dbVersion == 2) {
			return new RpcApiServiceOnSolidity();
		}

		return null;
	}

	@Bean
	public HttpApiOnSolidityService getHttpApiOnSolidityService() {
		boolean isSolidityNode = Args.getInstance().isSolidityNode();
		int dbVersion = Args.getInstance().getStorage().getDbVersion();
		if (!isSolidityNode && dbVersion == 2) {
			return new HttpApiOnSolidityService();
		}

		return null;
	}

	@Bean
	public TransactionCache transactionCache() {
		int dbVersion = Args.getInstance().getStorage().getDbVersion();
		if (dbVersion == 2) {
			return new TransactionCache("trans-cache");
		}

		return null;
	}

	@Bean
	@Conditional(NeedBeanCondition.class)
	public BackupRocksDBAspect backupRocksDBAspect() {
		return new BackupRocksDBAspect();
	}
}
