package io.midasprotocol.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;

public interface Actuator {

	boolean execute(TransactionResultCapsule result) throws ContractExeException;

	boolean validate() throws ContractValidateException;

	ByteString getOwnerAddress() throws InvalidProtocolBufferException;

	long calcFee();

}
