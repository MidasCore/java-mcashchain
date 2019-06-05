package io.midasprotocol.core.services.http;

import io.midasprotocol.api.GrpcAPI.ProposalList;
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
public class ListProposalsServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			ProposalList reply = wallet.getProposalList();
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
		doGet(request, response);
	}
}
