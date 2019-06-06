package io.midasprotocol.core.services.http;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI.Return;
import io.midasprotocol.api.GrpcAPI.Return.ResponseCode;
import io.midasprotocol.api.GrpcAPI.TransactionExtention;
import io.midasprotocol.common.crypto.Hash;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract.TriggerSmartContract;
import io.midasprotocol.protos.Protocol.Transaction;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class TriggerSmartContractServlet extends HttpServlet {

	@Autowired
	private Wallet wallet;

	public static String parseMethod(String methodSign, String params) {
		byte[] selector = new byte[4];
		System.arraycopy(Hash.sha3(methodSign.getBytes()), 0, selector, 0, 4);
		System.out.println(methodSign + ":" + Hex.toHexString(selector));
		if (StringUtils.isEmpty(params)) {
			return Hex.toHexString(selector);
		}
		String result = Hex.toHexString(selector) + params;
		return result;
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
		throws IOException {
		TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
		TransactionExtention.Builder trxExtBuilder = TransactionExtention.newBuilder();
		Return.Builder retBuilder = Return.newBuilder();

		try {
			String contract = request.getReader().lines()
				.collect(Collectors.joining(System.lineSeparator()));
			Util.checkBodySize(contract);
			JsonFormat.merge(contract, build);
			JSONObject jsonObject = JSONObject.parseObject(contract);
			String selector = jsonObject.getString("functionSelector");
			String parameter = jsonObject.getString("parameter");
			String data = parseMethod(selector, parameter);
			build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));

			long feeLimit = jsonObject.getLongValue("feeLimit");

			TransactionCapsule trxCap = wallet
				.createTransactionCapsule(build.build(), ContractType.TriggerSmartContract);

			Transaction.Builder txBuilder = trxCap.getInstance().toBuilder();
			Transaction.Raw.Builder rawBuilder = trxCap.getInstance().getRawData().toBuilder();
			rawBuilder.setFeeLimit(feeLimit);
			txBuilder.setRawData(rawBuilder);

			Transaction trx = wallet
				.triggerContract(build.build(), new TransactionCapsule(txBuilder.build()), trxExtBuilder,
					retBuilder);
			trxExtBuilder.setTransaction(trx);
			retBuilder.setResult(true).setCode(ResponseCode.SUCCESS);
		} catch (ContractValidateException e) {
			retBuilder.setResult(false).setCode(ResponseCode.CONTRACT_VALIDATE_ERROR)
				.setMessage(ByteString.copyFromUtf8(e.getMessage()));
		} catch (Exception e) {
			retBuilder.setResult(false).setCode(ResponseCode.OTHER_ERROR)
				.setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
		}
		trxExtBuilder.setResult(retBuilder);
		response.getWriter().println(Util.printTransactionExtention(trxExtBuilder.build()));
	}
}