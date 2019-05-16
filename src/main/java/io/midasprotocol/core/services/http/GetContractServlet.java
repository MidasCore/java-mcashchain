package io.midasprotocol.core.services.http;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.midasprotocol.api.GrpcAPI.BytesMessage;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Protocol.SmartContract;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class GetContractServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			String input = request.getParameter("value");
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("value", input);
			BytesMessage.Builder build = BytesMessage.newBuilder();
			JsonFormat.merge(jsonObject.toJSONString(), build);
			SmartContract smartContract = wallet.getContract(build.build());
			JSONObject jsonSmartContract = JSONObject
					.parseObject(JsonFormat.printToString(smartContract));
			response.getWriter().println(jsonSmartContract.toJSONString());
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
			SmartContract smartContract = wallet.getContract(build.build());
			JSONObject jsonSmartContract = JSONObject
					.parseObject(JsonFormat.printToString(smartContract));
			response.getWriter().println(jsonSmartContract.toJSONString());
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
