package io.midasprotocol.common.net.udp.message.discover;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.net.udp.message.Message;
import io.midasprotocol.common.overlay.discover.node.Node;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.protos.Discover;
import io.midasprotocol.protos.Discover.Endpoint;
import io.midasprotocol.protos.Discover.FindNeighbours;

import static io.midasprotocol.common.net.udp.message.UdpMessageTypeEnum.DISCOVER_FIND_NODE;

public class FindNodeMessage extends Message {

	private Discover.FindNeighbours findNeighbours;

	public FindNodeMessage(byte[] data) throws Exception {
		super(DISCOVER_FIND_NODE, data);
		this.findNeighbours = Discover.FindNeighbours.parseFrom(data);
	}

	public FindNodeMessage(Node from, byte[] targetId) {
		super(DISCOVER_FIND_NODE, null);
		Endpoint fromEndpoint = Endpoint.newBuilder()
			.setAddress(ByteString.copyFrom(ByteArray.fromString(from.getHost())))
			.setPort(from.getPort())
			.setNodeId(ByteString.copyFrom(from.getId()))
			.build();
		this.findNeighbours = FindNeighbours.newBuilder()
			.setFrom(fromEndpoint)
			.setTargetId(ByteString.copyFrom(targetId))
			.setTimestamp(System.currentTimeMillis())
			.build();
		this.data = this.findNeighbours.toByteArray();
	}

	public byte[] getTargetId() {
		return this.findNeighbours.getTargetId().toByteArray();
	}

	@Override
	public Node getFrom() {
		return Message.getNode(findNeighbours.getFrom());
	}

	@Override
	public String toString() {
		return "[findNeighbours: " + findNeighbours;
	}
}
