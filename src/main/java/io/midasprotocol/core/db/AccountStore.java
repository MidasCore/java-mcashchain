package io.midasprotocol.core.db;

import com.typesafe.config.ConfigObject;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "DB")
@Component
public class AccountStore extends TronStoreWithRevoking<AccountCapsule> {

	private static Map<String, byte[]> assertsAddress = new HashMap<>(); // key = name , value = address

	@Autowired
	private AccountStore(@Value("account") String dbName) {
		super(dbName);
	}

	public static void setAccount(com.typesafe.config.Config config) {
		List list = config.getObjectList("genesis.block.assets");
		for (int i = 0; i < list.size(); i++) {
			ConfigObject obj = (ConfigObject) list.get(i);
			String accountName = obj.get("accountName").unwrapped().toString();
			byte[] address = Wallet.decodeFromBase58Check(obj.get("address").unwrapped().toString());
			assertsAddress.put(accountName, address);
		}
	}

	@Override
	public AccountCapsule get(byte[] key) {
		byte[] value = revokingDB.getUnchecked(key);
		return ArrayUtils.isEmpty(value) ? null : new AccountCapsule(value);
	}

	/**
	 * Min TRX account.
	 */
	public AccountCapsule getBlackhole() {
		return getUnchecked(assertsAddress.get("Mcash Burn"));
	}

}
