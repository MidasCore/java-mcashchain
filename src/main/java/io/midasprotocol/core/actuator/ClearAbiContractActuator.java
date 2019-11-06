package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.runtime.config.VMConfig;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.ContractCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.db.AccountStore;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Protocol;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ClearAbiContractActuator extends AbstractActuator {

    ClearAbiContractActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        long fee = calcFee();
        try {
            Contract.ClearAbiContract usContract = contract.unpack(Contract.ClearAbiContract.class);
            byte[] contractAddress = usContract.getContractAddress().toByteArray();
            ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);
            deployedContract.clearABI();
            dbManager.getContractStore().put(contractAddress, deployedContract);

            ret.setStatus(fee, Protocol.Transaction.Result.Code.SUCCESS);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage(), e);
            ret.setStatus(fee, Protocol.Transaction.Result.Code.FAILED);
            throw new ContractExeException(e.getMessage());
        }
        return true;
    }

    @Override
    public boolean validate() throws ContractValidateException {
        if (!VMConfig.allowVmConstantinople()) {
            throw new ContractValidateException(
                "Contract type error, unexpected ClearABIContract");
        }

        if (this.contract == null) {
            throw new ContractValidateException("No contract!");
        }
        if (this.dbManager == null) {
            throw new ContractValidateException("No dbManager!");
        }
        if (!this.contract.is(Contract.ClearAbiContract.class)) {
            throw new ContractValidateException(
                "Contract type error, expected ClearAbiContract, actual " + contract.getClass());
        }
        final Contract.ClearAbiContract contract;
        try {
            contract = this.contract.unpack(Contract.ClearAbiContract.class);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
        if (!Wallet.addressValid(contract.getOwnerAddress().toByteArray())) {
            throw new ContractValidateException("Invalid address");
        }
        byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
        String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

        AccountStore accountStore = dbManager.getAccountStore();

        AccountCapsule accountCapsule = accountStore.get(ownerAddress);
        if (accountCapsule == null) {
            throw new ContractValidateException(
                "Account " + readableOwnerAddress + " not exists");
        }

        byte[] contractAddress = contract.getContractAddress().toByteArray();
        ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);

        if (deployedContract == null) {
            throw new ContractValidateException(
                "Contract not exists");
        }

        byte[] deployedContractOwnerAddress = deployedContract.getInstance().getOriginAddress()
            .toByteArray();

        if (!Arrays.equals(ownerAddress, deployedContractOwnerAddress)) {
            throw new ContractValidateException(
                "Account " + readableOwnerAddress + " is not the owner of the contract");
        }

        return true;
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(Contract.ClearAbiContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return 0;
    }

}