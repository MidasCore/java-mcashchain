package io.midasprotocol.core.services.http;

import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI.BytesMessage;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.TransactionInfo;
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
public class GetTransactionInfoByIdServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getParameter("value");
			TransactionInfo reply = wallet
				.getTransactionInfoById(ByteString.copyFrom(ByteArray.fromHexString(input)));
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
			String input = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			BytesMessage.Builder build = BytesMessage.newBuilder();
			JsonFormat.merge(input, build);
			TransactionInfo reply = wallet.getTransactionInfoById(build.getValue());
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
}