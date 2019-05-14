package org.tron.core.services.interfaceOnSolidity.http;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.entity.NodeInfo;
import org.tron.core.services.http.Util;
import org.tron.core.services.interfaceOnSolidity.NodeInfoOnSolidityService;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Component
@Slf4j(topic = "API")
public class GetNodeInfoOnSolidityServlet extends HttpServlet {

	@Autowired
	private NodeInfoOnSolidityService nodeInfoService;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			NodeInfo nodeInfo = nodeInfoService.getNodeInfo();
			response.getWriter().println(JSON.toJSONString(nodeInfo));
		} catch (Exception e) {
			logger.error("", e);
			try {
				response.getWriter().println(Util.printErrorMsg(e));
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			NodeInfo nodeInfo = nodeInfoService.getNodeInfo();
			response.getWriter().println(JSON.toJSONString(nodeInfo));
		} catch (Exception e) {
			logger.error("", e);
			try {
				response.getWriter().println(Util.printErrorMsg(e));
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
		}
	}
}
