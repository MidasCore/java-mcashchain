package io.midasprotocol.core.services.http;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.midasprotocol.api.GrpcAPI;
import io.midasprotocol.common.crypto.Hash;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.TransactionCapsule;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
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
public class TriggerConstantContractServlet extends HttpServlet {

    @Autowired
    private Wallet wallet;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    }

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

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        Contract.TriggerSmartContract.Builder build = Contract.TriggerSmartContract.newBuilder();
        GrpcAPI.TransactionExtension.Builder txExtBuilder = GrpcAPI.TransactionExtension.newBuilder();
        GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();

        try {
            String contract = request.getReader().lines()
                .collect(Collectors.joining(System.lineSeparator()));
            Util.checkBodySize(contract);
            JsonFormat.merge(contract, build);
            JSONObject jsonObject = JSONObject.parseObject(contract);
            String selector = jsonObject.getString("function_selector");
            String parameter = jsonObject.getString("parameter");
            String data = parseMethod(selector, parameter);
            build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));

            long feeLimit = jsonObject.getLongValue("fee_limit");

            TransactionCapsule txCap = wallet
                .createTransactionCapsule(build.build(), Protocol.Transaction.Contract.ContractType.TriggerSmartContract);

            Protocol.Transaction.Builder txBuilder = txCap.getInstance().toBuilder();
            Protocol.Transaction.Raw.Builder rawBuilder = txCap.getInstance().getRawData().toBuilder();
            rawBuilder.setFeeLimit(feeLimit);
            txBuilder.setRawData(rawBuilder);

            Protocol.Transaction tx = wallet
                .triggerConstantContract(build.build(), new TransactionCapsule(txBuilder.build()),
                    txExtBuilder,
                    retBuilder);
            txExtBuilder.setTransaction(tx);
            retBuilder.setResult(true).setCode(GrpcAPI.Return.ResponseCode.SUCCESS);
        } catch (ContractValidateException e) {
            retBuilder.setResult(false).setCode(GrpcAPI.Return.ResponseCode.CONTRACT_VALIDATE_ERROR)
                .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        } catch (Exception e) {
            retBuilder.setResult(false).setCode(GrpcAPI.Return.ResponseCode.OTHER_ERROR)
                .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        }
        txExtBuilder.setResult(retBuilder);
        response.getWriter().println(Util.printTransactionExtension(txExtBuilder.build()));
    }
}