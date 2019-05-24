package io.midasprotocol.core.db.api.index;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.index.disk.DiskIndex;
import com.googlecode.cqengine.persistence.disk.DiskPersistence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.db.common.WrappedByteArray;
import io.midasprotocol.core.db2.core.ITronChainBase;
import io.midasprotocol.protos.Protocol.Transaction;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.attribute;

@Component
@Slf4j(topic = "DB")
public class TransactionIndex extends AbstractIndex<TransactionCapsule, Transaction> {

	public static SimpleAttribute<WrappedByteArray, String> Transaction_ID;
	public static Attribute<WrappedByteArray, String> OWNERS;
	public static Attribute<WrappedByteArray, String> TOS;
	public static Attribute<WrappedByteArray, Long> TIMESTAMP;

	@Autowired
	public TransactionIndex(
			@Qualifier("transactionStore") final ITronChainBase<TransactionCapsule> database) {
		super(database);
	}

	@PostConstruct
	public void init() {
		initIndex(DiskPersistence.onPrimaryKeyInFile(Transaction_ID, indexPath));
//    index.addIndex(DiskIndex.onAttribute(Transaction_ID));
		index.addIndex(DiskIndex.onAttribute(OWNERS));
		index.addIndex(DiskIndex.onAttribute(TOS));
		index.addIndex(DiskIndex.onAttribute(TIMESTAMP));
	}

	@Override
	protected void setAttribute() {
		Transaction_ID =
				attribute("transaction id",
						bytes -> new TransactionCapsule(getObject(bytes)).getTransactionId().toString());
		OWNERS =
				attribute(String.class, "owner address",
						bytes -> getObject(bytes).getRawData().getContractList().stream()
								.map(TransactionCapsule::getOwner)
								.filter(Objects::nonNull)
								.map(ByteArray::toHexString)
								.collect(Collectors.toList()));
		TOS =
				attribute(String.class, "to address",
						bytes -> getObject(bytes).getRawData().getContractList().stream()
								.map(TransactionCapsule::getToAddress)
								.filter(Objects::nonNull)
								.map(ByteArray::toHexString)
								.collect(Collectors.toList()));
		TIMESTAMP =
				attribute("timestamp", bytes -> getObject(bytes).getRawData().getTimestamp());
	}
}
