package org.tron.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;

public interface Actuator {

	boolean execute(TransactionResultCapsule result) throws ContractExeException;

	boolean validate() throws ContractValidateException;

	ByteString getOwnerAddress() throws InvalidProtocolBufferException;

	long calcFee();

}
