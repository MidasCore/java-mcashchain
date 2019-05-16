package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.protos.Protocol;

@Slf4j(topic = "capsule")
public class BannedSupernodeCapsule implements ProtoCapsule<Protocol.BannedSupernode>,
		Comparable<BannedSupernodeCapsule> {
	private Protocol.BannedSupernode bannedSupernode;

	public BannedSupernodeCapsule(byte[] data) {
		try {
			this.bannedSupernode = Protocol.BannedSupernode.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage());
		}
	}

	public BannedSupernodeCapsule(ByteString address, long expiration) {
		this.bannedSupernode = Protocol.BannedSupernode.newBuilder()
				.setSupernodeAddress(address)
				.setExpiration(expiration)
				.build();
	}

	public BannedSupernodeCapsule(ByteString address) {
		this(address, -1);
	}

	public ByteString getSupernodeAddress() {
		return this.bannedSupernode.getSupernodeAddress();
	}

	public long getExpiration() {
		return this.bannedSupernode.getExpiration();
	}

	public void setExpiration(long expiration) {
		this.bannedSupernode = this.bannedSupernode.toBuilder().setExpiration(expiration).build();
	}

	@Override
	public int compareTo(BannedSupernodeCapsule o) {
		return Long.compare(getExpiration(), o.getExpiration());
	}

	@Override
	public byte[] getData() {
		return this.bannedSupernode.toByteArray();
	}

	@Override
	public Protocol.BannedSupernode getInstance() {
		return this.bannedSupernode;
	}
}
