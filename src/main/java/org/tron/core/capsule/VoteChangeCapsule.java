package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Protocol.Vote;
import org.tron.protos.Protocol.VoteChange;

@Slf4j(topic = "capsule")
public class VoteChangeCapsule implements ProtoCapsule<VoteChange> {

	private VoteChange voteChange;

	public VoteChangeCapsule(final VoteChange voteChange) {
		this.voteChange = voteChange;
	}

	public VoteChangeCapsule(final byte[] data) {
		try {
			this.voteChange = VoteChange.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
		}
	}

	public VoteChangeCapsule(ByteString address, Vote oldVote) {
		VoteChange.Builder builder = VoteChange.newBuilder().setAddress(address);
		if (oldVote != null) {
			builder.setOldVote(oldVote);
		} else {
			builder.clearOldVote();
		}
		this.voteChange = builder.build();
	}

	public VoteChangeCapsule(ByteString address) {
		this.voteChange = VoteChange.newBuilder().setAddress(address).build();
	}

	public ByteString getAddress() {
		return this.voteChange.getAddress();
	}

	public void setAddress(ByteString address) {
		this.voteChange = this.voteChange.toBuilder().setAddress(address).build();
	}

	public Vote getOldVote() {
		return this.voteChange.getOldVote();
	}

	public void setOldVote(Vote oldVote) {
		if (oldVote != null) {
			this.voteChange = this.voteChange.toBuilder()
					.setOldVote(oldVote)
					.build();
		} else {
			this.voteChange = this.voteChange.toBuilder()
					.clearOldVote()
					.build();
		}
	}

	public Vote getNewVote() {
		return this.voteChange.getNewVote();
	}

	public void clearNewVote() {
		this.voteChange = this.voteChange.toBuilder()
				.clearNewVote()
				.build();
	}

	public void setNewVote(ByteString voteAddress, long voteCount) {
		this.voteChange = this.voteChange.toBuilder()
				.setNewVote(Vote.newBuilder().setVoteAddress(voteAddress).setVoteCount(voteCount).build())
				.build();
	}

	public boolean hasOldVote() {
		return getOldVote() != null;
	}

	public boolean hasNewVote() {
		return getNewVote() != null;
	}

	public byte[] createDbKey() {
		return getAddress().toByteArray();
	}

	public String createReadableString() {
		return ByteArray.toHexString(getAddress().toByteArray());
	}

	@Override
	public byte[] getData() {
		return this.voteChange.toByteArray();
	}

	@Override
	public VoteChange getInstance() {
		return this.voteChange;
	}

}
