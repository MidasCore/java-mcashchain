package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.util.StakeUtil;
import io.midasprotocol.protos.Protocol;

@Slf4j(topic = "capsule")
public class StakeAccountCapsule implements ProtoCapsule<Protocol.StakeAccount>, Comparable<StakeAccountCapsule> {

	private Protocol.StakeAccount stakeAccount;

	/**
	 * get StakeAccount from bytes data.
	 */
	public StakeAccountCapsule(byte[] data) {
		try {
			this.stakeAccount = Protocol.StakeAccount.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage());
		}
	}

	/**
	 * initial account capsule.
	 */
	public StakeAccountCapsule(ByteString address) {
		this.stakeAccount = Protocol.StakeAccount.newBuilder()
				.setAddress(address)
				.build();
	}

	public StakeAccountCapsule(Protocol.StakeAccount stakeAccount) {
		this.stakeAccount = stakeAccount;
	}

	public StakeAccountCapsule(ByteString address, long stakeAmount, long votingPower) {
		this.stakeAccount = Protocol.StakeAccount.newBuilder()
				.setAddress(address)
				.setStakeAmount(stakeAmount)
				.setVotingPower(votingPower)
				.build();
	}

	@Override
	public int compareTo(StakeAccountCapsule o) {
		return Long.compare(o.getStakeAmount(), this.stakeAccount.getStakeAmount());
	}

	@Override
	public byte[] getData() {
		return this.stakeAccount.toByteArray();
	}

	@Override
	public Protocol.StakeAccount getInstance() {
		return this.stakeAccount;
	}

	public byte[] createDbKey() {
		return getAddress().toByteArray();
	}

	public String createReadableString() {
		return ByteArray.toHexString(getAddress().toByteArray());
	}

	public ByteString getAddress() {
		return this.stakeAccount.getAddress();
	}

	public long getStakeAmount() {
		return this.stakeAccount.getStakeAmount();
	}

	public long getVotingPower() {
		return this.stakeAccount.getVotingPower();
	}

	public void setStake(long stakeAmount) {
		long stakeAmountWithBonus = StakeUtil.getStakeAmountWithBonusFromStakeAmount(stakeAmount);
		long votingPower = StakeUtil.getVotingPowerFromStakeAmount(stakeAmount);
		this.stakeAccount = this.stakeAccount.toBuilder()
				.setStakeAmount(stakeAmount)
				.setStakeAmountWithBonus(stakeAmountWithBonus)
				.setVotingPower(votingPower)
				.build();
	}

	public long getStakeAmountWithBonus() {
		return this.stakeAccount.getStakeAmountWithBonus();
	}

}
