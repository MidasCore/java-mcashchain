package io.midasprotocol.core.services.interfaceOnSolidity.http;

import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.services.http.GetDelegatedResourceServlet;
import io.midasprotocol.core.services.interfaceOnSolidity.WalletOnSolidity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j(topic = "API")
public class GetDelegatedResourceOnSolidityServlet extends GetDelegatedResourceServlet {

	@Autowired
	private Wallet wallet;

	@Autowired
	private WalletOnSolidity walletOnSolidity;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		walletOnSolidity.futureGet(() -> super.doGet(request, response));
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		walletOnSolidity.futureGet(() -> super.doPost(request, response));
	}
}
