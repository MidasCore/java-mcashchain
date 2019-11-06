package io.midasprotocol.core.services.http;

import io.midasprotocol.core.Wallet;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
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
public class ClearAbiServlet extends HttpServlet {

    @Autowired
    private Wallet wallet;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {

    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            String contract = request.getReader().lines()
                .collect(Collectors.joining(System.lineSeparator()));
            Util.checkBodySize(contract);
            Contract.ClearAbiContract.Builder build = Contract.ClearAbiContract.newBuilder();
            JsonFormat.merge(contract, build);
            Protocol.Transaction tx = wallet
                .createTransactionCapsule(build.build(), Protocol.Transaction.Contract.ContractType.ClearAbiContract)
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