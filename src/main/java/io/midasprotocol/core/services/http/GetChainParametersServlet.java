package io.midasprotocol.core.services.http;

import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Component
@Slf4j(topic = "API")
public class GetChainParametersServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			response.getWriter().println(JsonFormat.printToString(wallet.getChainParameters()));
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
			try {
				response.getWriter().println(Util.printErrorMsg(e));
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		doPost(request, response);
	}
}