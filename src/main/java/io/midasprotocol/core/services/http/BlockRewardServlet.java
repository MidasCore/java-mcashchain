package io.midasprotocol.core.services.http;

import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.core.Wallet;
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

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getReader().lines()
					.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			GrpcAPI.BlockRewardPaginatedMessage.Builder builder =
					GrpcAPI.BlockRewardPaginatedMessage.newBuilder();

			JsonFormat.merge(input, builder);

			GrpcAPI.BlockRewardList blockRewards = wallet.getPaginatedBlockRewardList(
					builder.getAddress(), builder.getOffset(), builder.getLimit());
			if (blockRewards != null) {
				response.getWriter().println(JsonFormat.printToString(blockRewards));
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
