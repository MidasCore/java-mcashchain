package io.midasprotocol.core.services.http.solidity;

import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI.AccountPaginated;
import io.midasprotocol.api.GrpcAPI.TransactionList;
import io.midasprotocol.core.WalletSolidity;
import io.midasprotocol.core.services.http.JsonFormat;
import io.midasprotocol.core.services.http.Util;
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
public class GetTransactionsFromThisServlet extends HttpServlet {

	@Autowired
	private WalletSolidity walletSolidity;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
		try {
			String input = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			AccountPaginated.Builder builder = AccountPaginated.newBuilder();
			JsonFormat.merge(input, builder);
			AccountPaginated accountPaginated = builder.build();
			ByteString thisAddress = accountPaginated.getAccount().getAddress();
			long offset = accountPaginated.getOffset();
			long limit = accountPaginated.getLimit();
			if (thisAddress != null && offset >= 0 && limit >= 0) {
				TransactionList list = walletSolidity.getTransactionsFromThis(thisAddress, offset, limit);
				resp.getWriter().println(Util.printTransactionList(list));
			} else {
				resp.getWriter().print("{}");
			}
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
			try {
				resp.getWriter().println(e.getMessage());
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) {

	}
}
