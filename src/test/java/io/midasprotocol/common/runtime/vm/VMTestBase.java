package io.midasprotocol.common.runtime.vm;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.spongycastle.util.encoders.Hex;
import io.midasprotocol.common.application.ApplicationFactory;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.runtime.Runtime;
import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.common.storage.DepositImpl;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;

@Slf4j
public class VMTestBase {

	protected Manager manager;
	protected ApplicationContext context;
	protected String dbPath;
	protected Deposit rootDeposit;
	protected String OWNER_ADDRESS;
	protected Runtime runtime;

	@Before
	public void init() {
		dbPath = "output_" + this.getClass().getName();
		Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);

		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = "abd4b9367799eaa3197fecb144eb71de1e049abc";
		manager = context.getBean(Manager.class);
		rootDeposit = DepositImpl.createRoot(manager);
		rootDeposit.createAccount(Hex.decode(OWNER_ADDRESS), AccountType.Normal);
		rootDeposit.addBalance(Hex.decode(OWNER_ADDRESS), 30000000000000L);

		rootDeposit.commit();
	}

	@After
	public void destroy() {
		Args.clearParam();
		ApplicationFactory.create(context).shutdown();
		ApplicationFactory.create(context).shutdownServices();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.error("Release resources failure.");
		}
	}

}
