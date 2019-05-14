package org.tron.core.db;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.utils.Utils;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.witness.WitnessController;
import org.tron.protos.Protocol.Account;

public class ManagerForTest {

	private Manager dbManager;

	public ManagerForTest(Manager dbManager) {
		this.dbManager = dbManager;
	}

	private Map<ByteString, String> addTestWitnessAndAccount() {
		dbManager.getWitnesses().clear();
		return IntStream.range(0, 2)
				.mapToObj(
						i -> {
							ECKey ecKey = new ECKey(Utils.getRandom());
							String privateKey = ByteArray.toHexString(ecKey.getPrivKey().toByteArray());
							ByteString address = ByteString.copyFrom(ecKey.getAddress());

							WitnessCapsule witnessCapsule = new WitnessCapsule(address);
							dbManager.getWitnessStore().put(address.toByteArray(), witnessCapsule);
							dbManager.getWitnessController().addWitness(address);

							AccountCapsule accountCapsule =
									new AccountCapsule(Account.newBuilder().setAddress(address).build());
							dbManager.getAccountStore().put(address.toByteArray(), accountCapsule);

							return Maps.immutableEntry(address, privateKey);
						})
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private ByteString getWitnessAddress(long time) {
		WitnessController witnessController = dbManager.getWitnessController();
		return witnessController.getScheduledWitness(witnessController.getSlotAtTime(time));
	}

	public BlockCapsule createTestBlockCapsule(long time, long number, ByteString hash) {
		Map<ByteString, String> addressToProvateKeys = addTestWitnessAndAccount();
		ByteString witnessAddress = getWitnessAddress(time);

		BlockCapsule blockCapsule = new BlockCapsule(number, Sha256Hash.wrap(hash), time,
				witnessAddress);
		blockCapsule.generatedByMyself = true;
		blockCapsule.setMerkleRoot();
		blockCapsule.sign(ByteArray.fromHexString(addressToProvateKeys.get(witnessAddress)));
		return blockCapsule;
	}

	public boolean pushNTestBlock(int count) {
		try {
			for (int i = 1; i <= count; i++) {
				ByteString hash = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderHash()
						.getByteString();
				long time = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 3000L;
				long number = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() + 1;
				BlockCapsule blockCapsule = createTestBlockCapsule(time, number, hash);
				dbManager.pushBlock(blockCapsule);
			}
		} catch (Exception ignore) {
			return false;
		}
		return true;
	}
}