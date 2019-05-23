package io.midasprotocol.core.services.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Contract.ExchangeCreateContract;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class ExchangeCreateServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String contract = request.getReader().lines()
					.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(contract);
			ExchangeCreateContract.Builder build = ExchangeCreateContract.newBuilder();
			JsonFormat.merge(contract, build);
			Transaction tx = wallet
					.createTransactionCapsule(build.build(), ContractType.ExchangeCreateContract)
					.getInstance();
			response.getWriter().println(Util.printTransaction(tx));
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
