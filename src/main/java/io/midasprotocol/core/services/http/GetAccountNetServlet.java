package io.midasprotocol.core.services.http;

import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI.AccountNetMessage;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.Account;
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
public class GetAccountNetServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			String address = request.getParameter("address");
			AccountNetMessage reply = wallet
				.getAccountNet(ByteString.copyFrom(ByteArray.fromHexString(address)));
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
			String account = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(account);
			Account.Builder build = Account.newBuilder();
			JsonFormat.merge(account, build);
			AccountNetMessage reply = wallet.getAccountNet(build.getAddress());
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
