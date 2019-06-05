package io.midasprotocol.common.logsfilter.trigger;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class InternalTransactionPojo {

	@Getter
	@Setter
	private String hash;

	@Getter
	@Setter
	/* the amount of trx to transfer (calculated as sun) */
	private long callValue;

	@Getter
	@Setter
	private Map<Long, Long> tokenInfo = new HashMap<>();

	/* the address of the destination account (for message)
	 * In creation transaction the receive address is - 0 */
	@Getter
	@Setter
	private String transferToAddress;

	/* An unlimited size byte array specifying
	 * input [data] of the message call or
	 * Initialization code for a new contract */
	@Getter
	@Setter
	private String data;

	/*  Message sender address */
	@Getter
	@Setter
	private String callerAddress;

	@Getter
	@Setter
	private boolean rejected;

	@Getter
	@Setter
	private String note;
}
