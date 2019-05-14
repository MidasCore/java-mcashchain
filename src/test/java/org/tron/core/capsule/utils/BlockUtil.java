package org.tron.core.capsule.utils;


import com.google.protobuf.ByteString;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.config.args.GenesisBlock;
import org.tron.core.db.Manager;
import org.tron.core.witness.WitnessController;
import org.tron.protos.Protocol.Transaction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BlockUtil {

	/**
	 * create genesis block from transactions.
	 */
	public static BlockCapsule newGenesisBlockCapsule() {

		Args args = Args.getInstance();
		GenesisBlock genesisBlockArg = args.getGenesisBlock();
		List<Transaction> transactionList =
				genesisBlockArg.getAssets().stream()
						.map(key -> {
							byte[] address = key.getAddress();
							long balance = key.getBalance();
							return TransactionUtil.newGenesisTransaction(address, balance);
						})
						.collect(Collectors.toList());

		long timestamp = Long.parseLong(genesisBlockArg.getTimestamp());
		ByteString parentHash =
				ByteString.copyFrom(ByteArray.fromHexString(genesisBlockArg.getParentHash()));
		long number = Long.parseLong(genesisBlockArg.getNumber());

		BlockCapsule blockCapsule = new BlockCapsule(timestamp, parentHash, number, transactionList);

		blockCapsule.setMerkleRoot();
		blockCapsule.setWitness(
				"A new system must allow existing systems to be linked together without requiring any central control or coordination");
		blockCapsule.generatedByMyself = true;

		return blockCapsule;
	}

	public static boolean isParentOf(BlockCapsule blockCapsule1, BlockCapsule blockCapsule2) {
		return blockCapsule1.getBlockId().equals(blockCapsule2.getParentHash());
	}

	public static BlockCapsule createTestBlockCapsule(Manager dbManager, long time,
													  long number, ByteString hash, Map<ByteString, String> addressToProvateKeys) {
		WitnessController witnessController = dbManager.getWitnessController();
		ByteString witnessAddress =
				witnessController.getScheduledWitness(witnessController.getSlotAtTime(time));
		BlockCapsule blockCapsule = new BlockCapsule(number, Sha256Hash.wrap(hash), time,
				witnessAddress);
		blockCapsule.generatedByMyself = true;
		blockCapsule.setMerkleRoot();
		blockCapsule.sign(ByteArray.fromHexString(addressToProvateKeys.get(witnessAddress)));
		return blockCapsule;
	}
}