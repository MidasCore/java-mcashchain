package io.midasprotocol.program;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.WitnessCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol.AccountType;

import java.io.File;
import java.util.List;

@Slf4j
public class AccountVoteWitnessTest {

	private static ApplicationContext context;

	private static Manager dbManager;
	private static String dbPath = "output_witness_test";

	static {
		Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
	}

	/**
	 * init db.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
		// Args.setParam(new String[]{}, Constant.TEST_CONF);
		//  dbManager = new Manager();
		//  dbManager.init();
	}

	/**
	 * remo db when after test.
	 */
	@AfterClass
	public static void removeDb() {
		Args.clearParam();
		context.destroy();
		File dbFolder = new File(dbPath);
		if (deleteFolder(dbFolder)) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	private static Boolean deleteFolder(File index) {
		if (!index.isDirectory() || index.listFiles().length <= 0) {
			return index.delete();
		}
		for (File file : index.listFiles()) {
			if (null != file && !deleteFolder(file)) {
				return false;
			}
		}
		return index.delete();
	}

	@Test
	public void testAccountVoteWitness() {
		final List<AccountCapsule> accountCapsuleList = this.getAccountList();
		final List<WitnessCapsule> witnessCapsuleList = this.getWitnessList();
		accountCapsuleList.forEach(
			accountCapsule -> {
				dbManager
					.getAccountStore()
					.put(accountCapsule.getAddress().toByteArray(), accountCapsule);
				this.printAccount(accountCapsule.getAddress());
			});
		witnessCapsuleList.forEach(
			witnessCapsule ->
				dbManager
					.getWitnessStore()
					.put(witnessCapsule.getAddress().toByteArray(), witnessCapsule));
		dbManager.getWitnessController().updateWitness();
		this.printWitness(ByteString.copyFrom("000000000000000000001".getBytes()));
		this.printWitness(ByteString.copyFrom("000000000000000000002".getBytes()));
		this.printWitness(ByteString.copyFrom("000000000000000000003".getBytes()));
		this.printWitness(ByteString.copyFrom("000000000000000000004".getBytes()));
		this.printWitness(ByteString.copyFrom("000000000000000000005".getBytes()));
		this.printWitness(ByteString.copyFrom("000000000000000000006".getBytes()));
		this.printWitness(ByteString.copyFrom("000000000000000000007".getBytes()));
	}

	private void printAccount(final ByteString address) {
		final AccountCapsule accountCapsule = dbManager.getAccountStore().get(address.toByteArray());
		if (null == accountCapsule) {
			logger.info("address is {}  , account is null", address.toStringUtf8());
			return;
		}
		logger.info("address is {}", accountCapsule.getAddress().toStringUtf8());
		logger.info(
			"address is {}, countVote is {}",
			accountCapsule.getAddress().toStringUtf8(),
			accountCapsule.getVote() != null ?
				accountCapsule.getVote().getVoteCount() : 0);
	}

	private void printWitness(final ByteString address) {
		final WitnessCapsule witnessCapsule = dbManager.getWitnessStore().get(address.toByteArray());
		if (null == witnessCapsule) {
			logger.info("address is {}, witness is null", address.toStringUtf8());
			return;
		}
		logger.info(
			"address is {}, countVote is {}",
			witnessCapsule.getAddress().toStringUtf8(),
			witnessCapsule.getVoteCount());
	}

	private List<AccountCapsule> getAccountList() {
		final List<AccountCapsule> accountCapsuleList = Lists.newArrayList();
		final AccountCapsule accountTron =
			new AccountCapsule(
				ByteString.copyFrom("000000000000000000001".getBytes()),
				ByteString.copyFromUtf8("Tron"),
				AccountType.Normal);
		final AccountCapsule accountMarcus =
			new AccountCapsule(
				ByteString.copyFrom("000000000000000000002".getBytes()),
				ByteString.copyFromUtf8("Marcus"),
				AccountType.Normal);
		final AccountCapsule accountOlivier =
			new AccountCapsule(
				ByteString.copyFrom("000000000000000000003".getBytes()),
				ByteString.copyFromUtf8("Olivier"),
				AccountType.Normal);
		final AccountCapsule accountSasaXie =
			new AccountCapsule(
				ByteString.copyFrom("000000000000000000004".getBytes()),
				ByteString.copyFromUtf8("SasaXie"),
				AccountType.Normal);
		final AccountCapsule accountVivider =
			new AccountCapsule(
				ByteString.copyFrom("000000000000000000005".getBytes()),
				ByteString.copyFromUtf8("Vivider"),
				AccountType.Normal);
		// accountTron setVote
		accountTron.setVote(accountMarcus.getAddress(), 100);
		accountTron.setVote(accountOlivier.getAddress(), 100);
		accountTron.setVote(accountSasaXie.getAddress(), 100);
		accountTron.setVote(accountVivider.getAddress(), 100);

		// accountMarcus setVote
		accountMarcus.setVote(accountTron.getAddress(), 100);
		accountMarcus.setVote(accountOlivier.getAddress(), 100);
		accountMarcus.setVote(accountSasaXie.getAddress(), 100);
		accountMarcus.setVote(ByteString.copyFrom("000000000000000000006".getBytes()), 100);
		accountMarcus.setVote(ByteString.copyFrom("000000000000000000007".getBytes()), 100);
		// accountOlivier setVote
		accountOlivier.setVote(accountTron.getAddress(), 100);
		accountOlivier.setVote(accountMarcus.getAddress(), 100);
		accountOlivier.setVote(accountSasaXie.getAddress(), 100);
		accountOlivier.setVote(accountVivider.getAddress(), 100);
		// accountSasaXie setVote
		// accountVivider setVote
		accountCapsuleList.add(accountTron);
		accountCapsuleList.add(accountMarcus);
		accountCapsuleList.add(accountOlivier);
		accountCapsuleList.add(accountSasaXie);
		accountCapsuleList.add(accountVivider);
		return accountCapsuleList;
	}

	private List<WitnessCapsule> getWitnessList() {
		final List<WitnessCapsule> witnessCapsuleList = Lists.newArrayList();
		final WitnessCapsule witnessTron =
			new WitnessCapsule(
				ByteString.copyFrom("000000000000000000001".getBytes()),
				ByteString.copyFrom("000000000000000000001".getBytes()),
				0, "");
		final WitnessCapsule witnessOlivier =
			new WitnessCapsule(
				ByteString.copyFrom("000000000000000000003".getBytes()),
				ByteString.copyFrom("000000000000000000003".getBytes()),
				100, "");
		final WitnessCapsule witnessVivider =
			new WitnessCapsule(
				ByteString.copyFrom("000000000000000000005".getBytes()),
				ByteString.copyFrom("000000000000000000005".getBytes()),
				200, "");
		final WitnessCapsule witnessSenaLiu =
			new WitnessCapsule(
				ByteString.copyFrom("000000000000000000006".getBytes()),
				ByteString.copyFrom("000000000000000000006".getBytes()),
				300, "");

		logger.info(witnessTron.createReadableString());
		witnessCapsuleList.add(witnessTron);
		witnessCapsuleList.add(witnessOlivier);
		witnessCapsuleList.add(witnessVivider);
		witnessCapsuleList.add(witnessSenaLiu);
		return witnessCapsuleList;
	}
}
