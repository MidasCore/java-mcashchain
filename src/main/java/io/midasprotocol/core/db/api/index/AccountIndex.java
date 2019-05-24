package io.midasprotocol.core.db.api.index;

import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.persistence.disk.DiskPersistence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.db.common.WrappedByteArray;
import io.midasprotocol.core.db2.core.ITronChainBase;
import io.midasprotocol.protos.Protocol.Account;

import javax.annotation.PostConstruct;

import static com.googlecode.cqengine.query.QueryFactory.attribute;

@Component
@Slf4j(topic = "DB")
public class AccountIndex extends AbstractIndex<AccountCapsule, Account> {

	public static SimpleAttribute<WrappedByteArray, String> Account_ADDRESS;

	@Autowired
	public AccountIndex(@Qualifier("accountStore") final ITronChainBase<AccountCapsule> database) {
		super(database);
	}

	@PostConstruct
	public void init() {
		initIndex(DiskPersistence.onPrimaryKeyInFile(Account_ADDRESS, indexPath));
//    index.addIndex(DiskIndex.onAttribute(Account_ADDRESS));
	}

	@Override
	protected void setAttribute() {
		Account_ADDRESS = attribute("account address",
				bytes -> ByteArray.toHexString(bytes.getBytes()));
	}
}
