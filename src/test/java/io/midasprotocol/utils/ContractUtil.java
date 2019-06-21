package io.midasprotocol.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.midasprotocol.protos.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContractUtil {
	private static final Logger logger = LoggerFactory.getLogger("TestLogger");

	public static Protocol.SmartContract.ABI jsonStr2Abi(String jsonStr) {
		if (jsonStr == null) {
			return null;
		}

		JsonParser jsonParser = new JsonParser();
		JsonElement jsonElementRoot = jsonParser.parse(jsonStr);
		JsonArray jsonRoot = jsonElementRoot.getAsJsonArray();
		Protocol.SmartContract.ABI.Builder abiBuilder = Protocol.SmartContract.ABI.newBuilder();
		for (int index = 0; index < jsonRoot.size(); index++) {
			JsonElement abiItem = jsonRoot.get(index);
			boolean anonymous = abiItem.getAsJsonObject().get("anonymous") != null
				? abiItem.getAsJsonObject().get("anonymous").getAsBoolean() : false;
			final boolean constant = abiItem.getAsJsonObject().get("constant") != null
				? abiItem.getAsJsonObject().get("constant").getAsBoolean() : false;
			final String name = abiItem.getAsJsonObject().get("name") != null
				? abiItem.getAsJsonObject().get("name").getAsString() : null;
			JsonArray inputs = abiItem.getAsJsonObject().get("inputs") != null
				? abiItem.getAsJsonObject().get("inputs").getAsJsonArray() : null;
			final JsonArray outputs = abiItem.getAsJsonObject().get("outputs") != null
				? abiItem.getAsJsonObject().get("outputs").getAsJsonArray() : null;
			String type = abiItem.getAsJsonObject().get("type") != null
				? abiItem.getAsJsonObject().get("type").getAsString() : null;
			final boolean payable = abiItem.getAsJsonObject().get("payable") != null
				? abiItem.getAsJsonObject().get("payable").getAsBoolean() : false;
			final String stateMutability = abiItem.getAsJsonObject().get("stateMutability") != null
				? abiItem.getAsJsonObject().get("stateMutability").getAsString() : null;
			if (type == null) {
				logger.error("No type!");
				return null;
			}
			if (!type.equalsIgnoreCase("fallback") && null == inputs) {
				logger.error("No inputs!");
				return null;
			}

			Protocol.SmartContract.ABI.Entry.Builder entryBuilder = Protocol.SmartContract.ABI.Entry.newBuilder();
			entryBuilder.setAnonymous(anonymous);
			entryBuilder.setConstant(constant);
			if (name != null) {
				entryBuilder.setName(name);
			}

			/* { inputs : optional } since fallback function not requires inputs*/
			if (inputs != null) {
				for (int j = 0; j < inputs.size(); j++) {
					JsonElement inputItem = inputs.get(j);
					if (inputItem.getAsJsonObject().get("name") == null
						|| inputItem.getAsJsonObject().get("type") == null) {
						logger.error("Input argument invalid due to no name or no type!");
						return null;
					}
					String inputName = inputItem.getAsJsonObject().get("name").getAsString();
					String inputType = inputItem.getAsJsonObject().get("type").getAsString();
					Protocol.SmartContract.ABI.Entry.Param.Builder paramBuilder = Protocol.SmartContract.ABI.Entry.Param
						.newBuilder();
					JsonElement indexed = inputItem.getAsJsonObject().get("indexed");

					paramBuilder.setIndexed((indexed == null) ? false : indexed.getAsBoolean());
					paramBuilder.setName(inputName);
					paramBuilder.setType(inputType);
					entryBuilder.addInputs(paramBuilder.build());
				}
			}

			/* { outputs : optional } */
			if (outputs != null) {
				for (int k = 0; k < outputs.size(); k++) {
					JsonElement outputItem = outputs.get(k);
					if (outputItem.getAsJsonObject().get("name") == null
						|| outputItem.getAsJsonObject().get("type") == null) {
						logger.error("Output argument invalid due to no name or no type!");
						return null;
					}
					String outputName = outputItem.getAsJsonObject().get("name").getAsString();
					String outputType = outputItem.getAsJsonObject().get("type").getAsString();
					Protocol.SmartContract.ABI.Entry.Param.Builder paramBuilder = Protocol.SmartContract.ABI.Entry.Param
						.newBuilder();
					JsonElement indexed = outputItem.getAsJsonObject().get("indexed");

					paramBuilder.setIndexed((indexed == null) ? false : indexed.getAsBoolean());
					paramBuilder.setName(outputName);
					paramBuilder.setType(outputType);
					entryBuilder.addOutputs(paramBuilder.build());
				}
			}

			entryBuilder.setType(getEntryType(type));
			entryBuilder.setPayable(payable);
			if (stateMutability != null) {
				entryBuilder.setStateMutability(getStateMutability(stateMutability));
			}

			abiBuilder.addEntrys(entryBuilder.build());
		}

		return abiBuilder.build();
	}

	public static Protocol.SmartContract.ABI.Entry.EntryType getEntryType(String type) {
		switch (type) {
			case "constructor":
				return Protocol.SmartContract.ABI.Entry.EntryType.Constructor;
			case "function":
				return Protocol.SmartContract.ABI.Entry.EntryType.Function;
			case "event":
				return Protocol.SmartContract.ABI.Entry.EntryType.Event;
			case "fallback":
				return Protocol.SmartContract.ABI.Entry.EntryType.Fallback;
			default:
				return Protocol.SmartContract.ABI.Entry.EntryType.UNRECOGNIZED;
		}
	}

	public static Protocol.SmartContract.ABI.Entry.StateMutabilityType getStateMutability(
		String stateMutability) {
		switch (stateMutability) {
			case "pure":
				return Protocol.SmartContract.ABI.Entry.StateMutabilityType.Pure;
			case "view":
				return Protocol.SmartContract.ABI.Entry.StateMutabilityType.View;
			case "nonpayable":
				return Protocol.SmartContract.ABI.Entry.StateMutabilityType.Nonpayable;
			case "payable":
				return Protocol.SmartContract.ABI.Entry.StateMutabilityType.Payable;
			default:
				return Protocol.SmartContract.ABI.Entry.StateMutabilityType.UNRECOGNIZED;
		}
	}
}
