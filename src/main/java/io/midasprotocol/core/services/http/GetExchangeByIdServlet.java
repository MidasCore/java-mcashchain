package io.midasprotocol.core.services.http;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
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
public class GetExchangeByIdServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			JSONObject jsonObject = JSONObject.parseObject(input);
			long id = jsonObject.getLong("id");
			response.getWriter()
				.println(JsonFormat
					.printToString(wallet.getExchangeById(ByteString.copyFrom(ByteArray.fromLong(id)))));
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
		try {
			String input = request.getParameter("id");
			response.getWriter()
				.println(JsonFormat.printToString(wallet
					.getExchangeById(ByteString.copyFrom(ByteArray.fromLong(Long.parseLong(input))))));
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