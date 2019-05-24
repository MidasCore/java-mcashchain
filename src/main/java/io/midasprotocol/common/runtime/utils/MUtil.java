package io.midasprotocol.common.runtime.utils;

import io.midasprotocol.common.storage.Deposit;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.actuator.TransferActuator;
import io.midasprotocol.core.actuator.TransferAssetActuator;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Protocol;

public class MUtil {

	private MUtil() {
	}

	public static void transfer(Deposit deposit, byte[] fromAddress, byte[] toAddress, long amount)
			throws ContractValidateException {
		if (0 == amount) {
			return;
		}
		TransferActuator.validateForSmartContract(deposit, fromAddress, toAddress, amount);
		deposit.addBalance(toAddress, amount);
		deposit.addBalance(fromAddress, -amount);
	}

	public static void transferAllToken(Deposit deposit, byte[] fromAddress, byte[] toAddress) {
		AccountCapsule fromAccountCap = deposit.getAccount(fromAddress);
		Protocol.Account.Builder fromBuilder = fromAccountCap.getInstance().toBuilder();
		AccountCapsule toAccountCap = deposit.getAccount(toAddress);
		Protocol.Account.Builder toBuilder = toAccountCap.getInstance().toBuilder();
		fromAccountCap.getAssetMapV2().forEach((tokenId, amount) -> {
			toBuilder.putAssetV2(tokenId, toBuilder.getAssetV2Map().getOrDefault(tokenId, 0L) + amount);
			fromBuilder.putAssetV2(tokenId, 0L);
		});
		deposit.putAccountValue(fromAddress, new AccountCapsule(fromBuilder.build()));
		deposit.putAccountValue(toAddress, new AccountCapsule(toBuilder.build()));
	}

	public static void transferToken(Deposit deposit, byte[] fromAddress, byte[] toAddress,
									 String tokenId, long amount)
			throws ContractValidateException {
		if (0 == amount) {
			return;
		}
		TransferAssetActuator
				.validateForSmartContract(deposit, fromAddress, toAddress, tokenId.getBytes(), amount);
		deposit.addTokenBalance(toAddress, tokenId.getBytes(), amount);
		deposit.addTokenBalance(fromAddress, tokenId.getBytes(), -amount);
	}

	public static byte[] convertToTronAddress(byte[] address) {
		if (address.length == 20) {
			byte[] newAddress = new byte[21];
			byte[] temp = new byte[]{Wallet.getAddressPreFixByte()};
			System.arraycopy(temp, 0, newAddress, 0, temp.length);
			System.arraycopy(address, 0, newAddress, temp.length, address.length);
			address = newAddress;
		}
		return address;
	}
}
