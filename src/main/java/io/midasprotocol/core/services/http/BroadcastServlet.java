package io.midasprotocol.core.services.http;

import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.Transaction;
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
public class BroadcastServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			Transaction transaction = Util.packTransaction(input);
			GrpcAPI.Return retur = wallet.broadcastTransaction(transaction);
			response.getWriter().println(JsonFormat.printToString(retur));
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
