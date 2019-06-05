package io.midasprotocol.core.services.http;

import com.alibaba.fastjson.JSONObject;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.TransactionSign;
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
public class TransactionSignServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {

	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String contract = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(contract);
			JSONObject input = JSONObject.parseObject(contract);
			String strTransaction = input.getJSONObject("transaction").toJSONString();
			Transaction transaction = Util.packTransaction(strTransaction);
			JSONObject jsonTransaction = JSONObject.parseObject(JsonFormat.printToString(transaction));
			input.put("transaction", jsonTransaction);
			TransactionSign.Builder build = TransactionSign.newBuilder();
			JsonFormat.merge(input.toJSONString(), build);
			TransactionCapsule reply = wallet.getTransactionSign(build.build());
			if (reply != null) {
				response.getWriter().println(Util.printTransaction(reply.getInstance()));
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
