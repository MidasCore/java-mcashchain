package io.midasprotocol.common.overlay.server;

import io.midasprotocol.common.overlay.message.Message;
import io.midasprotocol.common.overlay.message.PingMessage;
import io.midasprotocol.common.overlay.message.PongMessage;
import io.midasprotocol.core.net.message.InventoryMessage;
import io.midasprotocol.core.net.message.TransactionsMessage;
import io.midasprotocol.protos.Protocol.Inventory.InventoryType;
import io.midasprotocol.protos.Protocol.ReasonCode;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.*;

@Slf4j(topic = "net")
@Component
@Scope("prototype")
public class MessageQueue {

	private static ScheduledExecutorService sendTimer = Executors.
		newSingleThreadScheduledExecutor(r -> new Thread(r, "sendTimer"));
	private volatile boolean sendMsgFlag = false;
	private volatile long sendTime;
	private Thread sendMsgThread;
	private Channel channel;
	private ChannelHandlerContext ctx = null;
	private Queue<MessageRoundtrip> requestQueue = new ConcurrentLinkedQueue<>();
	private BlockingQueue<Message> msgQueue = new LinkedBlockingQueue<>();
	private ScheduledFuture<?> sendTask;


	public void activate(ChannelHandlerContext ctx) {

		this.ctx = ctx;

		sendMsgFlag = true;

		sendTask = sendTimer.scheduleAtFixedRate(() -> {
			try {
				if (sendMsgFlag) {
					send();
				}
			} catch (Exception e) {
				logger.error("Unhandled exception", e);
			}
		}, 10, 10, TimeUnit.MILLISECONDS);

		sendMsgThread = new Thread(() -> {
			while (sendMsgFlag) {
				try {
					if (msgQueue.isEmpty()) {
						Thread.sleep(10);
						continue;
					}
					Message msg = msgQueue.take();
					ctx.writeAndFlush(msg.getSendData()).addListener((ChannelFutureListener) future -> {
						if (!future.isSuccess() && !channel.isDisconnect()) {
							logger.error("Fail send to {}, {}", ctx.channel().remoteAddress(), msg);
						}
					});
				} catch (Exception e) {
					logger.error("Fail send to {}, error info: {}", ctx.channel().remoteAddress(),
						e.getMessage());
				}
			}
		});
		sendMsgThread.setName("sendMsgThread-" + ctx.channel().remoteAddress());
		sendMsgThread.start();
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public boolean sendMessage(Message msg) {
		if (msg instanceof PingMessage && sendTime > System.currentTimeMillis() - 10_000) {
			return false;
		}
		if (needToLog(msg)) {
			logger.info("Send to {}, {} ", ctx.channel().remoteAddress(), msg);
		}
		channel.getNodeStatistics().messageStatistics.addTcpOutMessage(msg);
		sendTime = System.currentTimeMillis();
		if (msg.getAnswerMessage() != null) {
			requestQueue.add(new MessageRoundtrip(msg));
		} else {
			msgQueue.offer(msg);
		}
		return true;
	}

	public void receivedMessage(Message msg) {
		if (needToLog(msg)) {
			logger.info("Receive from {}, {}", ctx.channel().remoteAddress(), msg);
		}
		channel.getNodeStatistics().messageStatistics.addTcpInMessage(msg);
		MessageRoundtrip messageRoundtrip = requestQueue.peek();
		if (messageRoundtrip != null && messageRoundtrip.getMsg().getAnswerMessage() == msg
			.getClass()) {
			requestQueue.remove();
		}
	}

	public void close() {
		sendMsgFlag = false;
		if (sendTask != null && !sendTask.isCancelled()) {
			sendTask.cancel(false);
			sendTask = null;
		}
		if (sendMsgThread != null) {
			try {
				sendMsgThread.join(20);
				sendMsgThread = null;
			} catch (Exception e) {
				logger.warn("Join send thread failed, peer {}", ctx.channel().remoteAddress());
			}
		}
	}

	private boolean needToLog(Message msg) {
		if (msg instanceof PingMessage ||
			msg instanceof PongMessage ||
			msg instanceof TransactionsMessage) {
			return false;
		}

		if (msg instanceof InventoryMessage &&
			((InventoryMessage) msg).getInventoryType().equals(InventoryType.TRX)) {
			return false;
		}

		return true;
	}

	private void send() {
		MessageRoundtrip messageRoundtrip = requestQueue.peek();
		if (!sendMsgFlag || messageRoundtrip == null) {
			return;
		}
		if (messageRoundtrip.getRetryTimes() > 0 && !messageRoundtrip.hasToRetry()) {
			return;
		}
		if (messageRoundtrip.getRetryTimes() > 0) {
			channel.getNodeStatistics().nodeDisconnectedLocal(ReasonCode.PING_TIMEOUT);
			logger
				.warn("Wait {} timeout. close channel {}.", messageRoundtrip.getMsg().getAnswerMessage(),
					ctx.channel().remoteAddress());
			channel.close();
			return;
		}

		Message msg = messageRoundtrip.getMsg();

		ctx.writeAndFlush(msg.getSendData()).addListener((ChannelFutureListener) future -> {
			if (!future.isSuccess()) {
				logger.error("Fail send to {}, {}", ctx.channel().remoteAddress(), msg);
			}
		});

		messageRoundtrip.incRetryTimes();
		messageRoundtrip.saveTime();
	}

}
