package io.midasprotocol.core.capsule;

import com.beust.jcommander.internal.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.protos.Protocol;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j(topic = "capsule")
public class BlockRewardCapsule implements ProtoCapsule<Protocol.BlockReward> {

	private Protocol.BlockReward blockReward;

	public BlockRewardCapsule(final Protocol.BlockReward blockReward) {
		this.blockReward = blockReward;
	}

	public BlockRewardCapsule(final byte[] data) {
		try {
			this.blockReward = Protocol.BlockReward.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public BlockRewardCapsule(final long blockNumber) {
		this.blockReward = Protocol.BlockReward.newBuilder().setBlockNumber(blockNumber).build();
	}

	public void addReward(ByteString address, long amount, Protocol.BlockReward.RewardType rewardType) {
		this.blockReward = this.blockReward.toBuilder()
			.addRewards(
				Protocol.BlockReward.Reward.newBuilder()
					.setAddress(address)
					.setAmount(amount)
					.setType(rewardType).build()).build();
	}

	public void clearReward() {
		this.blockReward = this.blockReward.toBuilder().clearRewards().build();
	}

	public List<Protocol.BlockReward.Reward> getRewardsList() {
		if (this.blockReward.getRewardsList() != null) {
			return this.blockReward.getRewardsList();
		} else {
			return Lists.newArrayList();
		}
	}

	public byte[] createDbKey() {
		return ByteArray.fromLong(getBlockNumber());
	}

	public long getBlockNumber() {
		return this.blockReward.getBlockNumber();
	}

	@Override
	public byte[] getData() {
		return this.blockReward.toByteArray();
	}

	@Override
	public Protocol.BlockReward getInstance() {
		return this.blockReward;
	}
}
