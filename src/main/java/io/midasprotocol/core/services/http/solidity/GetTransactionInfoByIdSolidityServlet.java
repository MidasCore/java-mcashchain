package io.midasprotocol.core.services.http.solidity;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.midasprotocol.api.GrpcAPI.BytesMessage;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.services.http.JsonFormat;
import io.midasprotocol.core.services.http.Util;
import io.midasprotocol.protos.Protocol.TransactionInfo;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class GetTransactionInfoByIdSolidityServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getParameter("value");
			TransactionInfo transInfo = wallet.getTransactionInfoById(ByteString.copyFrom(
					ByteArray.fromHexString(input)));
			if (transInfo == null) {
				response.getWriter().println("{}");
			} else {
				response.getWriter().println(JsonFormat.printToString(transInfo));
			}
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
			try {
				response.getWriter().println(e.getMessage());
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getReader().lines()
					.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			BytesMessage.Builder build = BytesMessage.newBuilder();
			JsonFormat.merge(input, build);
			TransactionInfo transInfo = wallet.getTransactionInfoById(build.build().getValue());
			if (transInfo == null) {
				response.getWriter().println("{}");
			} else {
				response.getWriter().println(JsonFormat.printToString(transInfo));
			}
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
			try {
				response.getWriter().println(e.getMessage());
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}
}
