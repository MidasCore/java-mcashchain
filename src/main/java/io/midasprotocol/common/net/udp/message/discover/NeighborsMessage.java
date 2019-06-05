package io.midasprotocol.common.net.udp.message.discover;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.net.udp.message.Message;
import io.midasprotocol.common.overlay.discover.node.Node;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.protos.Discover;
import io.midasprotocol.protos.Discover.Endpoint;
import io.midasprotocol.protos.Discover.Neighbours;
import io.midasprotocol.protos.Discover.Neighbours.Builder;

import java.util.ArrayList;
import java.util.List;

import static io.midasprotocol.common.net.udp.message.UdpMessageTypeEnum.DISCOVER_NEIGHBORS;

public class NeighborsMessage extends Message {

	private Discover.Neighbours neighbours;

	public NeighborsMessage(byte[] data) throws Exception {
		super(DISCOVER_NEIGHBORS, data);
		this.neighbours = Discover.Neighbours.parseFrom(data);
	}

	public NeighborsMessage(Node from, List<Node> neighbours) {
		super(DISCOVER_NEIGHBORS, null);
		Builder builder = Neighbours.newBuilder()
			.setTimestamp(System.currentTimeMillis());

		neighbours.forEach(neighbour -> {
			Endpoint endpoint = Endpoint.newBuilder()
				.setAddress(ByteString.copyFrom(ByteArray.fromString(neighbour.getHost())))
				.setPort(neighbour.getPort())
				.setNodeId(ByteString.copyFrom(neighbour.getId()))
				.build();

			builder.addNeighbours(endpoint);
		});

		Endpoint fromEndpoint = Endpoint.newBuilder()
			.setAddress(ByteString.copyFrom(ByteArray.fromString(from.getHost())))
			.setPort(from.getPort())
			.setNodeId(ByteString.copyFrom(from.getId()))
			.build();

		builder.setFrom(fromEndpoint);

		this.neighbours = builder.build();

		this.data = this.neighbours.toByteArray();
	}

	public List<Node> getNodes() {
		List<Node> nodes = new ArrayList<>();
		neighbours.getNeighboursList().forEach(neighbour -> nodes.add(
			new Node(neighbour.getNodeId().toByteArray(),
				ByteArray.toStr(neighbour.getAddress().toByteArray()),
				neighbour.getPort())));
		return nodes;
	}

	@Override
	public Node getFrom() {
		return Message.getNode(neighbours.getFrom());
	}

	@Override
	public String toString() {
		return "[neighbours: " + neighbours;
	}

}
