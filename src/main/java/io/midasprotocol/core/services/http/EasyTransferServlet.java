package io.midasprotocol.core.services.http;

import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.api.GrpcAPI.EasyTransferMessage;
import io.midasprotocol.api.GrpcAPI.EasyTransferResponse;
import io.midasprotocol.api.GrpcAPI.Return.response_code;
import io.midasprotocol.common.crypto.ECKey;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.services.http.JsonFormat.ParseException;
import io.midasprotocol.protos.Contract.TransferContract;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;
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
public class EasyTransferServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {

	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
		EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
		try {
			String input = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(input);
			EasyTransferMessage.Builder build = EasyTransferMessage.newBuilder();
			JsonFormat.merge(input, build);
			byte[] privateKey = wallet.pass2Key(build.getPassPhrase().toByteArray());
			ECKey ecKey = ECKey.fromPrivate(privateKey);
			byte[] owner = ecKey.getAddress();
			TransferContract.Builder builder = TransferContract.newBuilder();
			builder.setOwnerAddress(ByteString.copyFrom(owner));
			builder.setToAddress(build.getToAddress());
			builder.setAmount(build.getAmount());

			TransactionCapsule transactionCapsule;
			transactionCapsule = wallet
				.createTransactionCapsule(builder.build(), ContractType.TransferContract);
			transactionCapsule.sign(privateKey);
			GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
			responseBuild.setTransaction(transactionCapsule.getInstance());
			responseBuild.setResult(retur);
			response.getWriter().println(Util.printEasyTransferResponse(responseBuild.build()));
		} catch (ParseException e) {
			logger.debug("ParseException: {}", e.getMessage());
		} catch (IOException e) {
			logger.debug("IOException: {}", e.getMessage());
		} catch (ContractValidateException e) {
			returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
				.setMessage(ByteString.copyFromUtf8(e.getMessage()));
			responseBuild.setResult(returnBuilder.build());
			try {
				response.getWriter().println(JsonFormat.printToString(responseBuild.build()));
			} catch (IOException ioe) {
				logger.debug("IOException: {}", ioe.getMessage());
			}
			return;
		} catch (Exception e) {
			logger.debug("Exception: {}", e.getMessage());
		}
	}
}
