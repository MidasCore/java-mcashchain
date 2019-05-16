package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import io.midasprotocol.protos.Protocol;

@Slf4j(topic = "capsule")
public class BannedWitnessCapsule implements ProtoCapsule<Protocol.BannedWitness>,
		Comparable<BannedWitnessCapsule> {
	private Protocol.BannedWitness bannedWitness;

	public BannedWitnessCapsule(byte[] data) {
		try {
			this.bannedWitness = Protocol.BannedWitness.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage());
		}
	}

	public BannedWitnessCapsule(ByteString address, long expirationTime) {
		this.bannedWitness = Protocol.BannedWitness.newBuilder()
				.setWitnessAddress(address)
				.setExpirationTime(expirationTime)
				.build();
	}

	public BannedWitnessCapsule(ByteString address) {
		this(address, -1);
	}

	public ByteString getWitnessAddress() {
		return this.bannedWitness.getWitnessAddress();
	}

	public long getExpirationTime() {
		return this.bannedWitness.getExpirationTime();
	}

	public void setExpiration(long expiration) {
		this.bannedWitness = this.bannedWitness.toBuilder().setExpirationTime(expiration).build();
	}

	@Override
	public int compareTo(BannedWitnessCapsule o) {
		return Long.compare(getExpirationTime(), o.getExpirationTime());
	}

	@Override
	public byte[] getData() {
		return this.bannedWitness.toByteArray();
	}

	@Override
	public Protocol.BannedWitness getInstance() {
		return this.bannedWitness;
	}
}
