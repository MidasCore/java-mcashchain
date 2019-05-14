package stest.tron.wallet.common.client.utils;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.tron.common.utils.ByteArray;
import stest.tron.wallet.common.client.Configuration;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HttpMethed {

	static HttpClient httpClient;
	static HttpPost httppost;
	static HttpResponse response;
	static Integer connectionTimeout = Configuration.getByPath("testng.conf")
			.getInt("defaultParameter.httpConnectionTimeout");
	static Integer soTimeout = Configuration.getByPath("testng.conf")
			.getInt("defaultParameter.httpSoTimeout");
	static String transactionString;
	static String transactionSignString;
	static JSONObject responseContent;
	static JSONObject signResponseContent;
	static JSONObject transactionApprovedListContent;

	static {
		PoolingClientConnectionManager pccm = new PoolingClientConnectionManager();
		pccm.setDefaultMaxPerRoute(20);
		pccm.setMaxTotal(100);

		httpClient = new DefaultHttpClient(pccm);
	}

	/**
	 * constructor.
	 */
	public static HttpResponse updateAccount(String httpNode, byte[] updateAccountAddress,
											 String accountName, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/updateaccount";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("account_name", str2hex(accountName));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(updateAccountAddress));
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse updateWitness(String httpNode, byte[] witnessAddress, String updateUrl,
											 String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/updatewitness";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("update_url", str2hex(updateUrl));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(witnessAddress));
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse voteWitnessAccount(String httpNode, byte[] ownerAddress,
												  JsonArray voteArray, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/votewitnessaccount";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.add("votes", voteArray);
			logger.info(userBaseObj2.toString());
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse createAccount(String httpNode, byte[] ownerAddress,
											 byte[] accountAddress, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createaccount";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("account_address", ByteArray.toHexString(accountAddress));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse createWitness(String httpNode, byte[] ownerAddress, String url) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createwitness";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("url", str2hex(url));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
			//transactionString = EntityUtils.toString(response.getEntity());
			//transactionSignString = gettransactionsign(httpNode,transactionString,fromKey);
			//response = broadcastTransaction(httpNode,transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse withdrawBalance(String httpNode, byte[] witnessAddress) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/withdrawbalance";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(witnessAddress));
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
			//transactionString = EntityUtils.toString(response.getEntity());
			//transactionSignString = gettransactionsign(httpNode,transactionString,fromKey);
			//response = broadcastTransaction(httpNode,transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse sendCoin(String httpNode, byte[] fromAddress, byte[] toAddress,
										Long amount, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createtransaction";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("to_address", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(fromAddress));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse sendCoin(String httpNode, byte[] fromAddress, byte[] toAddress,
										Long amount, String[] managerKeys) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createtransaction";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("to_address", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(fromAddress));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			transactionSignString = EntityUtils.toString(response.getEntity());
			HttpResponse getSignWeightResponse;
			HttpResponse getTransactionApprovedListResponse;

			for (String key : managerKeys) {
				transactionSignString = gettransactionsign(httpNode, transactionSignString, key);
				getSignWeightResponse = getSignWeight(httpNode, transactionSignString);
				signResponseContent = parseResponseContent(getSignWeightResponse);
				logger.info("-----------sign information-----------------");
				printJsonContent(signResponseContent);
				getSignWeightResponse = getTransactionApprovedList(httpNode, transactionSignString);
				signResponseContent = parseResponseContent(getSignWeightResponse);
				logger.info("-----------get Transaction Approved List-----------------");
				printJsonContent(signResponseContent);


			}
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static String sendCoinGetTxid(String httpNode, byte[] fromAddress, byte[] toAddress,
										 Long amount, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createtransaction";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("to_address", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(fromAddress));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		responseContent = HttpMethed.parseStringContent(transactionString);
		logger.info(responseContent.getString("txID"));
		return responseContent.getString("txID");
	}

	/**
	 * constructor.
	 */
	public static HttpResponse createProposal(String httpNode, byte[] ownerAddress, Long proposalKey,
											  Long proposalValue, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/proposalcreate";
			JsonObject userBaseObj2 = new JsonObject();
			JsonObject proposalMap = new JsonObject();
			proposalMap.addProperty("key", proposalKey);
			proposalMap.addProperty("value", proposalValue);
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.add("parameters", proposalMap);

			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse approvalProposal(String httpNode, byte[] ownerAddress,
												Integer proposalId, Boolean isAddApproval, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/proposalapprove";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("proposal_id", proposalId);
			userBaseObj2.addProperty("is_add_approval", isAddApproval);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse deleteProposal(String httpNode, byte[] ownerAddress,
											  Integer proposalId, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/proposaldelete";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("proposal_id", proposalId);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse accountPermissionUpdate(String httpNode, byte[] ownerAddress,
													   JsonObject ownerObject, JsonObject witnessObject, JsonObject activesObject, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/accountpermissionupdate";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.add("owner", ownerObject);
			//userBaseObj2.add("witness", witnessObject);
			userBaseObj2.add("actives", activesObject);
			logger.info(userBaseObj2.toString());
			response = createConnect(requestUrl, userBaseObj2);

			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse exchangeCreate(String httpNode, byte[] ownerAddress,
											  String firstTokenId, Long firstTokenBalance,
											  String secondTokenId, Long secondTokenBalance, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/exchangecreate";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("first_token_id", str2hex(firstTokenId));
			userBaseObj2.addProperty("first_token_balance", firstTokenBalance);
			userBaseObj2.addProperty("second_token_id", str2hex(secondTokenId));
			userBaseObj2.addProperty("second_token_balance", secondTokenBalance);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse exchangeInject(String httpNode, byte[] ownerAddress,
											  Integer exchangeId, String tokenId, Long quant, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/exchangeinject";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("exchange_id", exchangeId);
			userBaseObj2.addProperty("token_id", str2hex(tokenId));
			userBaseObj2.addProperty("quant", quant);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse exchangeWithdraw(String httpNode, byte[] ownerAddress,
												Integer exchangeId, String tokenId, Long quant, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/exchangewithdraw";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("exchange_id", exchangeId);
			userBaseObj2.addProperty("token_id", str2hex(tokenId));
			userBaseObj2.addProperty("quant", quant);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse exchangeTransaction(String httpNode, byte[] ownerAddress,
												   Integer exchangeId, String tokenId, Long quant, Long expected, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/exchangetransaction";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("exchange_id", exchangeId);
			userBaseObj2.addProperty("token_id", str2hex(tokenId));
			userBaseObj2.addProperty("quant", quant);
			userBaseObj2.addProperty("expected", expected);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse assetIssue(String httpNode, byte[] ownerAddress, String name,
										  String abbr, Long totalSupply, Integer trxNum, Integer num, Long startTime, Long endTime,
										  Integer voteScore, Integer precision, String description, String url, Long freeAssetNetLimit,
										  Long publicFreeAssetNetLimit, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createassetissue";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("name", str2hex(name));
			userBaseObj2.addProperty("abbr", str2hex(abbr));
			userBaseObj2.addProperty("total_supply", totalSupply);
			userBaseObj2.addProperty("trx_num", trxNum);
			userBaseObj2.addProperty("num", num);
			userBaseObj2.addProperty("precision", precision);
			userBaseObj2.addProperty("start_time", startTime);
			userBaseObj2.addProperty("end_time", endTime);
			userBaseObj2.addProperty("vote_score", voteScore);
			userBaseObj2.addProperty("description", str2hex(description));
			userBaseObj2.addProperty("url", str2hex(url));
			userBaseObj2.addProperty("free_asset_net_limit", freeAssetNetLimit);
			userBaseObj2.addProperty("public_free_asset_net_limit", publicFreeAssetNetLimit);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse transferAsset(String httpNode, byte[] ownerAddress,
											 byte[] toAddress, String assetIssueById, Long amount, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/transferasset";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("to_address", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("asset_name", str2hex(assetIssueById));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse deployContract(String httpNode, String name, String abi,
											  String bytecode,
											  Long bandwidthLimit, Long feeLimit, Integer consumeUserResourcePercent,
											  Long originEnergyLimit,
											  Long callValue, Integer tokenId, Long tokenValue, byte[] ownerAddress, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/deploycontract";
			JsonObject userBaseObj2 = new JsonObject();
			//userBaseObj2.addProperty("name", str2hex(name));
			userBaseObj2.addProperty("name", name);
			userBaseObj2.addProperty("abi", abi);
			userBaseObj2.addProperty("bytecode", bytecode);
			userBaseObj2.addProperty("bandwidth_limit", bandwidthLimit);
			userBaseObj2.addProperty("fee_limit", feeLimit);
			userBaseObj2.addProperty("consume_user_resource_percent", consumeUserResourcePercent);
			userBaseObj2.addProperty("origin_energy_limit", originEnergyLimit);
			userBaseObj2.addProperty("call_value", callValue);
			userBaseObj2.addProperty("token_id", tokenId);
			userBaseObj2.addProperty("tokenValue", tokenValue);
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static String deployContractGetTxid(String httpNode, String name, String abi,
											   String bytecode,
											   Long bandwidthLimit, Long feeLimit, Integer consumeUserResourcePercent,
											   Long originEnergyLimit,
											   Long callValue, Integer tokenId, Long tokenValue, byte[] ownerAddress, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/deploycontract";
			JsonObject userBaseObj2 = new JsonObject();
			//userBaseObj2.addProperty("name", str2hex(name));
			userBaseObj2.addProperty("name", name);
			userBaseObj2.addProperty("abi", abi);
			userBaseObj2.addProperty("bytecode", bytecode);
			userBaseObj2.addProperty("bandwidth_limit", bandwidthLimit);
			userBaseObj2.addProperty("fee_limit", feeLimit);
			userBaseObj2.addProperty("consume_user_resource_percent", consumeUserResourcePercent);
			userBaseObj2.addProperty("origin_energy_limit", originEnergyLimit);
			userBaseObj2.addProperty("call_value", callValue);
			userBaseObj2.addProperty("token_id", tokenId);
			userBaseObj2.addProperty("call_token_value", tokenValue);
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			logger.info(userBaseObj2.toString());
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		responseContent = HttpMethed.parseStringContent(transactionString);
		return responseContent.getString("txID");
	}


	/**
	 * constructor.
	 */
	public static String triggerContractGetTxid(String httpNode, byte[] ownerAddress,
												String contractAddress, String functionSelector, String parameter, Long feeLimit,
												Long callValue, Integer tokenId, Long tokenValue, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/triggersmartcontract";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("contract_address", contractAddress);
			userBaseObj2.addProperty("function_selector", functionSelector);
			userBaseObj2.addProperty("parameter", parameter);
			userBaseObj2.addProperty("fee_limit", feeLimit);
			userBaseObj2.addProperty("call_value", callValue);
			userBaseObj2.addProperty("token_id", tokenId);
			userBaseObj2.addProperty("call_token_value", tokenValue);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);

			transactionSignString = gettransactionsign(httpNode, parseStringContent(transactionString)
					.getString("transaction"), fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		responseContent = HttpMethed.parseStringContent(transactionSignString);
		return responseContent.getString("txID");
	}


	/**
	 * constructor.
	 */
	public static HttpResponse participateAssetIssue(String httpNode, byte[] toAddress,
													 byte[] ownerAddress, String assetIssueById, Long amount, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/participateassetissue";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("to_address", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("asset_name", str2hex(assetIssueById));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse updateAssetIssue(String httpNode, byte[] ownerAddress,
												String description, String url, Long newLimit, Long newPublicLimit, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/updateasset";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("url", str2hex(url));
			userBaseObj2.addProperty("description", str2hex(description));
			userBaseObj2.addProperty("new_limit", newLimit);
			userBaseObj2.addProperty("new_public_limit", newPublicLimit);
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static Boolean verificationResult(HttpResponse response) {
		if (response.getStatusLine().getStatusCode() != 200) {
			return false;
		}
		Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
		responseContent = HttpMethed.parseResponseContent(response);
		HttpMethed.printJsonContent(responseContent);
		return Boolean.valueOf(responseContent.getString("result")).booleanValue();
	}

	/**
	 * constructor.
	 */
	public static HttpResponse freezeBalance(String httpNode, byte[] ownerAddress,
											 Long frozenBalance, Integer frozenDuration, Integer resourceCode, String fromKey) {
		return freezeBalance(httpNode, ownerAddress, frozenBalance, frozenDuration, resourceCode,
				null, fromKey);
	}

	/**
	 * constructor.
	 */
	public static HttpResponse freezeBalance(String httpNode, byte[] ownerAddress,
											 Long frozenBalance, Integer frozenDuration, Integer resourceCode, byte[] receiverAddress,
											 String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/freezebalance";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("frozen_balance", frozenBalance);
			userBaseObj2.addProperty("frozen_duration", frozenDuration);
			if (resourceCode == 0) {
				userBaseObj2.addProperty("resource", "BANDWIDTH");
			}
			if (resourceCode == 1) {
				userBaseObj2.addProperty("resource", "ENERGY");
			}
			if (receiverAddress != null) {
				userBaseObj2.addProperty("receiver_address", ByteArray.toHexString(receiverAddress));
			}
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse unFreezeBalance(String httpNode, byte[] ownerAddress,
											   Integer resourceCode, String fromKey) {
		return unFreezeBalance(httpNode, ownerAddress, resourceCode, null, fromKey);
	}

	/**
	 * constructor.
	 */
	public static HttpResponse unFreezeBalance(String httpNode, byte[] ownerAddress,
											   Integer resourceCode, byte[] receiverAddress, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/unfreezebalance";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			if (resourceCode == 0) {
				userBaseObj2.addProperty("resource", "BANDWIDTH");
			}
			if (resourceCode == 1) {
				userBaseObj2.addProperty("resource", "ENERGY");
			}
			if (receiverAddress != null) {
				userBaseObj2.addProperty("receiver_address", ByteArray.toHexString(receiverAddress));
			}
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static String gettransactionsign(String httpNode, String transactionString,
											String privateKey) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/gettransactionsign";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("transaction", transactionString);
			userBaseObj2.addProperty("privateKey", privateKey);
			response = createConnect(requestUrl, userBaseObj2);
			transactionSignString = EntityUtils.toString(response.getEntity());
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return transactionSignString;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse broadcastTransaction(String httpNode, String transactionSignString) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/broadcasttransaction";
			httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
					connectionTimeout);
			httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, soTimeout);
			httppost = new HttpPost(requestUrl);
			httppost.setHeader("Content-type", "application/json; charset=utf-8");
			httppost.setHeader("Connection", "Close");
			if (transactionSignString != null) {
				StringEntity entity = new StringEntity(transactionSignString, Charset.forName("UTF-8"));
				entity.setContentEncoding("UTF-8");
				entity.setContentType("application/json");
				httppost.setEntity(entity);
			}
			response = httpClient.execute(httppost);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}

		responseContent = HttpMethed.parseResponseContent(response);
		Integer times = 0;

		while (times++ <= 10 && responseContent.getString("code") != null && responseContent
				.getString("code").equalsIgnoreCase("SERVER_BUSY")) {
			logger.info("retry num are " + times);
			try {
				response = httpClient.execute(httppost);
			} catch (Exception e) {
				e.printStackTrace();
				httppost.releaseConnection();
				return null;
			}
			responseContent = HttpMethed.parseResponseContent(response);
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		httppost.releaseConnection();
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAccount(String httpNode, byte[] queryAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getaccount";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("address", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAccountFromSolidity(String httpSolidityNode, byte[] queryAddress) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getaccount";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("address", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getContract(String httpNode, String contractAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getcontract";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", contractAddress);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getSignWeight(String httpNode, String transactionSignString) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getsignweight";
			httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
					connectionTimeout);
			httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, soTimeout);
			httppost = new HttpPost(requestUrl);
			httppost.setHeader("Content-type", "application/json; charset=utf-8");
			httppost.setHeader("Connection", "Close");
			if (transactionSignString != null) {
				StringEntity entity = new StringEntity(transactionSignString, Charset.forName("UTF-8"));
				entity.setContentEncoding("UTF-8");
				entity.setContentType("application/json");
				httppost.setEntity(entity);
			}
			response = httpClient.execute(httppost);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		//httppost.releaseConnection();
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionApprovedList(String httpNode,
														  String transactionSignString) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getapprovedlist";
			httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
					connectionTimeout);
			httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, soTimeout);
			httppost = new HttpPost(requestUrl);
			httppost.setHeader("Content-type", "application/json; charset=utf-8");
			httppost.setHeader("Connection", "Close");
			if (transactionSignString != null) {
				StringEntity entity = new StringEntity(transactionSignString, Charset.forName("UTF-8"));
				entity.setContentEncoding("UTF-8");
				entity.setContentType("application/json");
				httppost.setEntity(entity);
			}
			response = httpClient.execute(httppost);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		//httppost.releaseConnection();
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse listExchanges(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/listexchanges";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse listExchangesFromSolidity(String httpSolidityNode) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/listexchanges";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse listNodes(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/listnodes";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getNextmaintenanceTime(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getnextmaintenancetime";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getChainParameter(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getchainparameters";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getNodeInfo(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getnodeinfo";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse listwitnesses(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/listwitnesses";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse listwitnessesFromSolidity(String httpSolidityNode) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/listwitnesses";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse listProposals(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/listproposals";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getExchangeById(String httpNode, Integer exchangeId) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getexchangebyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("id", exchangeId);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getExchangeByIdFromSolidity(String httpSolidityNode,
														   Integer exchangeId) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getexchangebyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("id", exchangeId);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getProposalById(String httpNode, Integer proposalId) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getproposalbyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("id", proposalId);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueListByName(String httpNode, String name) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getassetissuelistbyname";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", str2hex(name));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueListByNameFromSolidity(String httpSolidityNode,
																   String name) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getassetissuelistbyname";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", str2hex(name));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueById(String httpNode, String assetIssueId) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getassetissuebyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", assetIssueId);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueByIdFromSolidity(String httpSolidityNode,
															 String assetIssueId) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getassetissuebyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", assetIssueId);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionById(String httpNode, String txid) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/gettransactionbyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", txid);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionByIdFromSolidity(String httpSolidityNode, String txid) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/gettransactionbyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", txid);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionInfoById(String httpNode, String txid) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/gettransactioninfobyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", txid);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionInfoByIdFromSolidity(String httpSolidityNode,
																  String txid) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/gettransactioninfobyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", txid);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionCountByBlocknumFromSolidity(String httpSolidityNode,
																		 long blocknum) {
		try {
			String requestUrl =
					"http://" + httpSolidityNode + "/walletsolidity/gettransactioncountbyblocknum";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("num", blocknum);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionsFromThisFromSolidity(String httpSolidityNode,
																   byte[] fromAddress, long offset, long limit) {
		try {
			Map<String, String> map1 = new HashMap<String, String>();
			Map<String, Object> map = new HashMap<String, Object>();
			map1.put("address", ByteArray.toHexString(fromAddress));
			map.put("account", map1);
			map.put("offset", offset);
			map.put("limit", limit);
			String requestUrl = "http://" + httpSolidityNode + "/walletextension/gettransactionsfromthis";
			String jsonStr = new Gson().toJson(map);
			JsonObject jsonObj = new JsonParser().parse(jsonStr).getAsJsonObject();
			response = createConnect(requestUrl, jsonObj);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getTransactionsToThisFromSolidity(String httpSolidityNode,
																 byte[] toAddress, long offset, long limit) {
		try {
			Map<String, String> map1 = new HashMap<String, String>();
			Map<String, Object> map = new HashMap<String, Object>();
			map1.put("address", ByteArray.toHexString(toAddress));
			map.put("account", map1);
			map.put("offset", offset);
			map.put("limit", limit);
			String requestUrl = "http://" + httpSolidityNode + "/walletextension/gettransactionstothis";
			String jsonStr = new Gson().toJson(map);
			JsonObject jsonObj = new JsonParser().parse(jsonStr).getAsJsonObject();
			response = createConnect(requestUrl, jsonObj);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueByName(String httpNode, String name) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getassetissuebyname";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", str2hex(name));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueByNameFromSolidity(String httpSolidityNode, String name) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getassetissuebyname";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", str2hex(name));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static Long getBalance(String httpNode, byte[] queryAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getaccount";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("address", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		responseContent = HttpMethed.parseResponseContent(response);
		//HttpMethed.printJsonContent(responseContent);
		//httppost.releaseConnection();
		return Long.parseLong(responseContent.get("balance").toString());
	}


	/**
	 * constructor.
	 */
	public static HttpResponse getAccountNet(String httpNode, byte[] queryAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getaccountnet";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("address", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAccountReource(String httpNode, byte[] queryAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getaccountresource";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("address", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getNowBlock(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getnowblock";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getNowBlockFromSolidity(String httpSolidityNode) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getnowblock";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static void waitToProduceOneBlock(String httpNode) {
		response = HttpMethed.getNowBlock(httpNode);
		responseContent = HttpMethed.parseResponseContent(response);
		responseContent = HttpMethed.parseStringContent(responseContent.get("block_header").toString());
		responseContent = HttpMethed.parseStringContent(responseContent.get("raw_data").toString());
		Integer currentBlockNum = Integer.parseInt(responseContent.get("number").toString());
		Integer nextBlockNum = 0;
		Integer times = 0;
		while (nextBlockNum <= currentBlockNum && times++ <= 3) {
			response = HttpMethed.getNowBlock(httpNode);
			responseContent = HttpMethed.parseResponseContent(response);
			responseContent = HttpMethed.parseStringContent(responseContent.get("block_header")
					.toString());
			responseContent = HttpMethed.parseStringContent(responseContent.get("raw_data").toString());
			nextBlockNum = Integer.parseInt(responseContent.get("number").toString());
			try {
				Thread.sleep(3500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * constructor.
	 */
	public static void waitToProduceOneBlockFromSolidity(String httpNode, String httpSolidityNode) {
		response = HttpMethed.getNowBlock(httpNode);
		responseContent = HttpMethed.parseResponseContent(response);
		responseContent = HttpMethed.parseStringContent(responseContent.get("block_header").toString());
		responseContent = HttpMethed.parseStringContent(responseContent.get("raw_data").toString());
		Integer currentBlockNum = Integer.parseInt(responseContent.get("number").toString());
		Integer nextBlockNum = 0;
		Integer times = 0;
		while (nextBlockNum <= currentBlockNum && times++ <= 3) {
			response = HttpMethed.getNowBlockFromSolidity(httpSolidityNode);
			responseContent = HttpMethed.parseResponseContent(response);
			responseContent = HttpMethed.parseStringContent(responseContent.get("block_header")
					.toString());
			responseContent = HttpMethed.parseStringContent(responseContent.get("raw_data").toString());
			nextBlockNum = Integer.parseInt(responseContent.get("number").toString());
			try {
				Thread.sleep(3500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getBlockByNum(String httpNode, Integer blockNUm) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getblockbynum";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("num", blockNUm);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getBlockByNumFromSolidity(String httpSolidityNode, Integer blockNum) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getblockbynum";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("num", blockNum);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getBlockByLimitNext(String httpNode, Integer startNum,
												   Integer endNum) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getblockbylimitnext";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("startNum", startNum);
			userBaseObj2.addProperty("endNum", endNum);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getBlockByLastNum(String httpNode, Integer num) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getblockbylatestnum";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("num", num);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getBlockById(String httpNode, String blockId) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getblockbyid";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", blockId);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getDelegatedResource(
			String httpNode, byte[] fromAddress, byte[] toAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getdelegatedresource";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("fromAddress", ByteArray.toHexString(fromAddress));
			userBaseObj2.addProperty("toAddress", ByteArray.toHexString(toAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getDelegatedResourceFromSolidity(
			String httpSolidityNode, byte[] fromAddress, byte[] toAddress) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getdelegatedresource";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("fromAddress", ByteArray.toHexString(fromAddress));
			userBaseObj2.addProperty("toAddress", ByteArray.toHexString(toAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getDelegatedResourceAccountIndex(String httpNode,
																byte[] queryAddress) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getdelegatedresourceaccountindex";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getDelegatedResourceAccountIndexFromSolidity(String httpSolidityNode,
																			byte[] queryAddress) {
		try {
			String requestUrl =
					"http://" + httpSolidityNode + "/walletsolidity/getdelegatedresourceaccountindex";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", ByteArray.toHexString(queryAddress));
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse createConnect(String url) {
		return createConnect(url, null);
	}

	/**
	 * constructor.
	 */
	public static HttpResponse createConnect(String url, JsonObject requestBody) {
		try {
			httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
					connectionTimeout);
			httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, soTimeout);
			httppost = new HttpPost(url);
			httppost.setHeader("Content-type", "application/json; charset=utf-8");
			httppost.setHeader("Connection", "Close");
			if (requestBody != null) {
				StringEntity entity = new StringEntity(requestBody.toString(), Charset.forName("UTF-8"));
				entity.setContentEncoding("UTF-8");
				entity.setContentType("application/json");
				httppost.setEntity(entity);
			}
			response = httpClient.execute(httppost);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse getAssetissueList(String httpNode) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getassetissuelist";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getAssetIssueListFromSolidity(String httpSolidityNode) {
		try {
			String requestUrl = "http://" + httpSolidityNode + "/walletsolidity/getassetissuelist";
			response = createConnect(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getPaginatedAssetissueList(String httpNode, Integer offset,
														  Integer limit) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getpaginatedassetissuelist";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("offset", offset);
			userBaseObj2.addProperty("limit", limit);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getPaginatedAssetissueListFromSolidity(String httpSolidityNode,
																	  Integer offset, Integer limit) {
		try {
			String requestUrl =
					"http://" + httpSolidityNode + "/walletsolidity/getpaginatedassetissuelist";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("offset", offset);
			userBaseObj2.addProperty("limit", limit);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse getPaginatedProposalList(String httpNode, Integer offset,
														Integer limit) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getpaginatedproposallist";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("offset", offset);
			userBaseObj2.addProperty("limit", limit);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse getPaginatedExchangeList(String httpNode, Integer offset,
														Integer limit) {
		try {
			String requestUrl = "http://" + httpNode + "/wallet/getpaginatedexchangelist";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("offset", offset);
			userBaseObj2.addProperty("limit", limit);
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse updateSetting(String httpNode, byte[] ownerAddress,
											 String contractAddress, Integer consumeUserResourcePercent, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/updatesetting";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("contract_address", contractAddress);
			userBaseObj2.addProperty("consume_user_resource_percent", consumeUserResourcePercent);
			logger.info(userBaseObj2.toString());
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse updateEnergyLimit(String httpNode, byte[] ownerAddress,
												 String contractAddress, Integer originEnergyLimit, String fromKey) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/updateenergylimit";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("owner_address", ByteArray.toHexString(ownerAddress));
			userBaseObj2.addProperty("contract_address", contractAddress);
			userBaseObj2.addProperty("origin_energy_limit", originEnergyLimit);
			logger.info(userBaseObj2.toString());
			response = createConnect(requestUrl, userBaseObj2);
			transactionString = EntityUtils.toString(response.getEntity());
			transactionSignString = gettransactionsign(httpNode, transactionString, fromKey);
			logger.info(transactionString);
			logger.info(transactionSignString);
			response = broadcastTransaction(httpNode, transactionSignString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse createAddress(String httpNode, String value) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/createaddress";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("value", str2hex(value));
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse generateAddress(String httpNode) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/generateaddress";
			JsonObject userBaseObj2 = new JsonObject();
			response = createConnect(requestUrl, userBaseObj2);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static HttpResponse validateAddress(String httpNode, String address) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/validateaddress";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("address", address);
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse easyTransfer(String httpNode, String value, byte[] toAddress,
											Long amount) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/easytransfer";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("toAddress", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("passPhrase", str2hex(value));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse easyTransferByPrivate(String httpNode, String privateKey,
													 byte[] toAddress, Long amount) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/easytransferbyprivate";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("privateKey", privateKey);
			userBaseObj2.addProperty("toAddress", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("amount", amount);
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse easyTransferAsset(String httpNode, String value, byte[] toAddress,
												 Long amount, String assetId) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/easytransferasset";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("toAddress", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("passPhrase", str2hex(value));
			userBaseObj2.addProperty("amount", amount);
			userBaseObj2.addProperty("assetId", assetId);
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}

	/**
	 * constructor.
	 */
	public static HttpResponse easyTransferAssetByPrivate(String httpNode, String privateKey,
														  byte[] toAddress, Long amount, String assetId) {
		try {
			final String requestUrl = "http://" + httpNode + "/wallet/easytransferassetbyprivate";
			JsonObject userBaseObj2 = new JsonObject();
			userBaseObj2.addProperty("privateKey", privateKey);
			userBaseObj2.addProperty("toAddress", ByteArray.toHexString(toAddress));
			userBaseObj2.addProperty("amount", amount);
			userBaseObj2.addProperty("assetId", assetId);
			response = createConnect(requestUrl, userBaseObj2);
			logger.info(userBaseObj2.toString());
			transactionString = EntityUtils.toString(response.getEntity());
			logger.info(transactionString);
		} catch (Exception e) {
			e.printStackTrace();
			httppost.releaseConnection();
			return null;
		}
		return response;
	}


	/**
	 * constructor.
	 */
	public static void disConnect() {
		httppost.releaseConnection();
	}

	/**
	 * constructor.
	 */
	public static JSONObject parseResponseContent(HttpResponse response) {
		try {
			String result = EntityUtils.toString(response.getEntity());
			StringEntity entity = new StringEntity(result, Charset.forName("UTF-8"));
			response.setEntity(entity);
			JSONObject obj = JSONObject.parseObject(result);
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * constructor.
	 */
	public static JSONObject parseStringContent(String content) {
		try {
			JSONObject obj = JSONObject.parseObject(content);
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * constructor.
	 */
	public static void printJsonContent(JSONObject responseContent) {
		logger.info("----------------------------Print JSON Start---------------------------");
		for (String str : responseContent.keySet()) {
			logger.info(str + ":" + responseContent.get(str));
		}
		logger.info("JSON content size are: " + responseContent.size());
		logger.info("----------------------------Print JSON End-----------------------------");
	}

	/**
	 * constructor.
	 */
	public static String str2hex(String str) {
		char[] chars = "0123456789ABCDEF".toCharArray();
		StringBuilder sb = new StringBuilder("");
		byte[] bs = str.getBytes();
		int bit;
		for (int i = 0; i < bs.length; i++) {
			bit = (bs[i] & 0x0f0) >> 4;
			sb.append(chars[bit]);
			bit = bs[i] & 0x0f;
			sb.append(chars[bit]);
			// sb.append(' ');
		}
		return sb.toString().trim();
	}
}
