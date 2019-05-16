package io.midasprotocol.core.services.http;

import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.protos.Protocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class BlockRewardServlet extends HttpServlet {
	@Autowired
	private Wallet wallet;

	@Autowired
	private Manager dbManager;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			String address = request.getParameter("address");
			int offset = request.getParameter("offset") != null ?
					Integer.parseInt(request.getParameter("offset")):
					0;
			int limit = request.getParameter("limit") != null ?
					Integer.parseInt(request.getParameter("limit"))
					: 50;
			GrpcAPI.BlockRewardList reply = wallet.getPaginatedBlockRewardList(
					ByteString.copyFrom(ByteArray.fromHexString(address)),
					offset, limit);
			if (reply != null) {
				response.getWriter().println(JsonFormat.printToString(reply));
			} else {
				response.getWriter().println("{}");
			}
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
			try {
				response.getWriter().println(Util.printErrorMsg(e));
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String address = request.getReader().lines()
					.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(address);
			Protocol.BlockReward blockReward =
					dbManager.getBlockRewardStore().get(ByteArray.fromHexString(address)).getInstance();
			if (blockReward != null) {
				response.getWriter().println(JsonFormat.printToString(blockReward));
			} else {
				response.getWriter().println("{}");
			}
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
			try {
				response.getWriter().println(Util.printErrorMsg(e));
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}
}
