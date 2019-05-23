package io.midasprotocol.common.runtime.vm;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import io.midasprotocol.common.crypto.Hash;
import io.midasprotocol.common.logsfilter.trigger.ContractLogTrigger;
import io.midasprotocol.common.logsfilter.trigger.ContractTrigger;
import io.midasprotocol.common.runtime.utils.MUtil;
import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.ContractCapsule;
import io.midasprotocol.protos.Protocol.SmartContract.ABI;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LogInfoTriggerParser {

	private Long blockNum;
	private Long blockTimestamp;
	private String txId;
	private String originAddress;

	public LogInfoTriggerParser(Long blockNum,
								Long blockTimestamp,
								byte[] txId, byte[] originAddress) {

		this.blockNum = blockNum;
		this.blockTimestamp = blockTimestamp;
		this.txId = ArrayUtils.isEmpty(txId) ? "" : Hex.toHexString(txId);
		this.originAddress =
				ArrayUtils.isEmpty(originAddress) ? "" : Wallet.encodeBase58Check(originAddress);

	}

	public static String getEntrySignature(ABI.Entry entry) {
		String signature = entry.getName() + "(";
		StringBuilder builder = new StringBuilder();
		for (ABI.Entry.Param param : entry.getInputsList()) {
			if (builder.length() > 0) {
				builder.append(",");
			}
			builder.append(param.getType());
		}
		signature += builder.toString() + ")";
		return signature;
	}

	public List<ContractTrigger> parseLogInfos(List<LogInfo> logInfos, Deposit deposit) {

		List<ContractTrigger> list = new LinkedList<>();
		if (logInfos == null || logInfos.size() <= 0) {
			return list;
		}

		Map<String, ABI.Entry> fullMap = new HashMap<>();
		Map<String, String> signMap = new HashMap<>();

		for (LogInfo logInfo : logInfos) {

			byte[] contractAddress = MUtil.convertToTronAddress(logInfo.getAddress());
			String strContractAddr =
					ArrayUtils.isEmpty(contractAddress) ? "" : Wallet.encodeBase58Check(contractAddress);
			if (signMap.get(strContractAddr) != null) {
				continue;
			}
			ContractCapsule contract = deposit.getContract(contractAddress);
			if (contract == null) {
				signMap.put(strContractAddr, originAddress); // mark as found.
				continue;
			}
			ABI abi = contract.getInstance().getAbi();
			String creatorAddr = Wallet.encodeBase58Check(
					MUtil.convertToTronAddress(contract.getInstance().getOriginAddress().toByteArray()));
			signMap.put(strContractAddr, creatorAddr); // mark as found.

			// calculate the sha3 of the event signature first.
			if (abi != null && abi.getEntrysCount() > 0) {
				for (ABI.Entry entry : abi.getEntrysList()) {
					if (entry.getType() != ABI.Entry.EntryType.Event || entry.getAnonymous()) {
						continue;
					}
					String signature = getEntrySignature(entry);
					String sha3 = Hex.toHexString(Hash.sha3(signature.getBytes()));
					fullMap.put(strContractAddr + "_" + sha3, entry);
					signMap.put(strContractAddr + "_" + sha3, signature);
				}
			}
		}

		int index = 1;
		for (LogInfo logInfo : logInfos) {

			byte[] contractAddress = MUtil.convertToTronAddress(logInfo.getAddress());
			String strContractAddr =
					ArrayUtils.isEmpty(contractAddress) ? "" : Wallet.encodeBase58Check(contractAddress);

			List<DataWord> topics = logInfo.getTopics();
			ABI.Entry entry = null;
			String signature = "";
			if (topics != null && topics.size() > 0 && !ArrayUtils.isEmpty(topics.get(0).getData())
					&& fullMap.size() > 0) {
				String firstTopic = topics.get(0).toString();
				entry = fullMap.get(strContractAddr + "_" + firstTopic);
				signature = signMap.get(strContractAddr + "_" + firstTopic);
			}

			boolean isEvent = (entry != null);
			ContractTrigger event;
			if (isEvent) {
				event = new LogEventWrapper();
				((LogEventWrapper) event).setTopicList(logInfo.getClonedTopics());
				((LogEventWrapper) event).setData(logInfo.getClonedData());
				((LogEventWrapper) event).setEventSignature(signature);
				((LogEventWrapper) event).setAbiEntry(entry);
			} else {
				event = new ContractLogTrigger();
				((ContractLogTrigger) event).setTopicList(logInfo.getHexTopics());
				((ContractLogTrigger) event).setData(logInfo.getHexData());
			}
			String creatorAddr = signMap.get(strContractAddr);
			event.setUniqueId(txId + "_" + index);
			event.setTransactionId(txId);
			event.setContractAddress(strContractAddr);
			event.setOriginAddress(originAddress);
			event.setCallerAddress("");
			event.setCreatorAddress(StringUtils.isEmpty(creatorAddr) ? "" : creatorAddr);
			event.setBlockNumber(blockNum);
			event.setTimeStamp(blockTimestamp);

			list.add(event);
			index++;
		}
		return list;
	}
}
