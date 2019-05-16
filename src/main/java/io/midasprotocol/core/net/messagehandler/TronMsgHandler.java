package io.midasprotocol.core.net.messagehandler;

import io.midasprotocol.core.exception.P2pException;
import io.midasprotocol.core.net.message.TronMessage;
import io.midasprotocol.core.net.peer.PeerConnection;

public interface TronMsgHandler {

	void processMessage(PeerConnection peer, TronMessage msg) throws P2pException;

}
