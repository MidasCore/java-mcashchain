package io.midasprotocol.common.net.udp.message.discover;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.net.udp.message.Message;
import io.midasprotocol.common.overlay.discover.node.Node;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.protos.Discover;
import io.midasprotocol.protos.Discover.Endpoint;

import static io.midasprotocol.common.net.udp.message.UdpMessageTypeEnum.DISCOVER_PING;

public class PingMessage extends Message {

	private Discover.PingMessage pingMessage;

	public PingMessage(byte[] data) throws Exception {
		super(DISCOVER_PING, data);
		this.pingMessage = Discover.PingMessage.parseFrom(data);
	}

	public PingMessage(Node from, Node to) {
		super(DISCOVER_PING, null);
		Endpoint fromEndpoint = Endpoint.newBuilder()
			.setNodeId(ByteString.copyFrom(from.getId()))
			.setPort(from.getPort())
			.setAddress(ByteString.copyFrom(ByteArray.fromString(from.getHost())))
			.build();
		Endpoint toEndpoint = Endpoint.newBuilder()
			.setNodeId(ByteString.copyFrom(to.getId()))
			.setPort(to.getPort())
			.setAddress(ByteString.copyFrom(ByteArray.fromString(to.getHost())))
			.build();
		this.pingMessage = Discover.PingMessage.newBuilder()
			.setVersion(Args.getInstance().getNodeP2pVersion())
			.setFrom(fromEndpoint)
			.setTo(toEndpoint)
			.setTimestamp(System.currentTimeMillis())
			.build();
		this.data = this.pingMessage.toByteArray();
	}

	public int getVersion() {
		return this.pingMessage.getVersion();
	}

	public Node getTo() {
		Endpoint to = this.pingMessage.getTo();
		Node node = new Node(to.getNodeId().toByteArray(),
			ByteArray.toStr(to.getAddress().toByteArray()), to.getPort());
		return node;
	}

	@Override
	public Node getFrom() {
		return Message.getNode(pingMessage.getFrom());
	}

	@Override
	public String toString() {
		return "[pingMessage: " + pingMessage;
	}

}
