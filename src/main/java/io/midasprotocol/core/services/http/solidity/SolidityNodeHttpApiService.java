package io.midasprotocol.core.services.http.solidity;

import io.midasprotocol.common.application.Service;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.services.http.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j(topic = "API")
public class SolidityNodeHttpApiService implements Service {

	private int port = Args.getInstance().getSolidityHttpPort();

	private Server server;

	@Autowired
	private GetAccountServlet getAccountServlet;


	@Autowired
	private GetTransactionByIdSolidityServlet getTransactionByIdServlet;
	@Autowired
	private GetTransactionInfoByIdSolidityServlet getTransactionInfoByIdServlet;
	@Autowired
	private GetTransactionsFromThisServlet getTransactionsFromThisServlet;
	@Autowired
	private GetTransactionsToThisServlet getTransactionsToThisServlet;
	@Autowired
	private GetTransactionCountByBlockNumServlet getTransactionCountByBlockNumServlet;
	@Autowired
	private GetDelegatedResourceServlet getDelegatedResourceServlet;
	@Autowired
	private GetDelegatedResourceAccountIndexServlet getDelegatedResourceAccountIndexServlet;
	@Autowired
	private GetExchangeByIdServlet getExchangeByIdServlet;
	@Autowired
	private ListExchangesServlet listExchangesServlet;

	@Autowired
	private ListWitnessesServlet listWitnessesServlet;
	@Autowired
	private GetAssetIssueListServlet getAssetIssueListServlet;
	@Autowired
	private GetPaginatedAssetIssueListServlet getPaginatedAssetIssueListServlet;
	@Autowired
	private GetAssetIssueByNameServlet getAssetIssueByNameServlet;
	@Autowired
	private GetAssetIssueByIdServlet getAssetIssueByIdServlet;
	@Autowired
	private GetAssetIssueListByNameServlet getAssetIssueListByNameServlet;
	@Autowired
	private GetNowBlockServlet getNowBlockServlet;
	@Autowired
	private GetBlockByNumServlet getBlockByNumServlet;

	@Autowired
	private GetNodeInfoServlet getNodeInfoServlet;

	@Override
	public void init() {

	}

	@Override
	public void init(Args args) {

	}

	@Override
	public void start() {
		Args args = Args.getInstance();
		try {
			server = new Server(port);
			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
			context.setContextPath("/");
			server.setHandler(context);

			// same as FullNode
			context.addServlet(new ServletHolder(getAccountServlet), "/walletsolidity/getaccount");
			context.addServlet(new ServletHolder(listWitnessesServlet), "/walletsolidity/listwitnesses");
			context.addServlet(new ServletHolder(getAssetIssueListServlet),
				"/walletsolidity/getassetissuelist");
			context.addServlet(new ServletHolder(getPaginatedAssetIssueListServlet),
				"/walletsolidity/getpaginatedassetissuelist");
			context.addServlet(new ServletHolder(getAssetIssueByNameServlet),
				"/walletsolidity/getassetissuebyname");
			context.addServlet(new ServletHolder(getAssetIssueByIdServlet),
				"/walletsolidity/getassetissuebyid");
			context.addServlet(new ServletHolder(getAssetIssueListByNameServlet),
				"/walletsolidity/getassetissuelistbyname");
			context.addServlet(new ServletHolder(getNowBlockServlet), "/walletsolidity/getnowblock");
			context.addServlet(new ServletHolder(getBlockByNumServlet), "/walletsolidity/getblockbynum");
			context.addServlet(new ServletHolder(getDelegatedResourceServlet),
				"/walletsolidity/getdelegatedresource");
			context.addServlet(new ServletHolder(getDelegatedResourceAccountIndexServlet),
				"/walletsolidity/getdelegatedresourceaccountindex");
			context
				.addServlet(new ServletHolder(getExchangeByIdServlet), "/walletsolidity/getexchangebyid");
			context.addServlet(new ServletHolder(listExchangesServlet), "/walletsolidity/listexchanges");

			// only for SolidityNode
			context.addServlet(new ServletHolder(getTransactionByIdServlet),
				"/walletsolidity/gettransactionbyid");
			context
				.addServlet(new ServletHolder(getTransactionInfoByIdServlet),
					"/walletsolidity/gettransactioninfobyid");
			context
				.addServlet(new ServletHolder(getTransactionCountByBlockNumServlet),
					"/walletsolidity/gettransactioncountbyblocknum");

			// for extension api
			if (args.isWalletExtensionApi()) {
				context.addServlet(new ServletHolder(getTransactionsFromThisServlet),
					"/walletextension/gettransactionsfromthis");
				context
					.addServlet(new ServletHolder(getTransactionsToThisServlet),
						"/walletextension/gettransactionstothis");
			}

			context.addServlet(new ServletHolder(getNodeInfoServlet), "/wallet/getnodeinfo");

			server.start();
		} catch (Exception e) {
			logger.debug("IOException: {}", e.getMessage());
		}
	}

	@Override
	public void stop() {
		try {
			server.stop();
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
		}
	}
}
