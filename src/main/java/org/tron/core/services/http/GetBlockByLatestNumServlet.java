package org.tron.core.services.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.BlockList;
import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.core.Wallet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class GetBlockByLatestNumServlet extends HttpServlet {

	private static final long BLOCK_LIMIT_NUM = 100;
	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			long getNum = Long.parseLong(request.getParameter("num"));
			if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
				BlockList reply = wallet.getBlockByLatestNum(getNum);
				if (reply != null) {
					response.getWriter().println(Util.printBlockList(reply));
					return;
				}
			}
			response.getWriter().println("{}");
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
			String input = request.getReader().lines()
					.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			NumberMessage.Builder build = NumberMessage.newBuilder();
			JsonFormat.merge(input, build);
			long getNum = build.getNum();
			if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
				BlockList reply = wallet.getBlockByLatestNum(getNum);
				if (reply != null) {
					response.getWriter().println(JsonFormat.printToString(reply));
					return;
				}
			}
			response.getWriter().println("{}");
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