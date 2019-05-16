package io.midasprotocol.common.runtime;

import io.midasprotocol.common.runtime.vm.program.InternalTransaction.TrxType;
import io.midasprotocol.common.runtime.vm.program.ProgramResult;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.core.exception.VMIllegalException;


public interface Runtime {

	boolean isCallConstant() throws ContractValidateException;

	void execute() throws ContractValidateException, ContractExeException, VMIllegalException;

	void go();

	TrxType getTrxType();

	void finalization();

	ProgramResult getResult();

	String getRuntimeError();

	void setEnableEventLinstener(boolean enableEventLinstener);
}
