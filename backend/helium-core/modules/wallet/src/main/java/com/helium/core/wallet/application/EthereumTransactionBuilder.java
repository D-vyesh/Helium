package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.blockchain.EthereumRpcClient;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EthereumTransactionBuilder implements UnsignedTransactionBuilder {
    private static final BigDecimal WEI_PER_ETH = new BigDecimal("1000000000000000000");
    private final EthereumRpcClient ethereumRpcClient;
    private final String sourceAddress;

    public EthereumTransactionBuilder(
        EthereumRpcClient ethereumRpcClient,
        @Value("${helium.wallet.custody.eth.from-address:}") String sourceAddress
    ) {
        this.ethereumRpcClient = ethereumRpcClient;
        this.sourceAddress = sourceAddress == null ? "" : sourceAddress.trim();
    }

    @Override
    public String assetCode() { return "ETH"; }

    @Override
    public UnsignedTransactionDraft build(Withdrawal withdrawal, FeeTier feeTier) {
        if (!"ETH".equals(withdrawal.networkCode())) {
            throw new WalletValidationException("ETH builder requires the ETH network");
        }
        if (sourceAddress.isBlank()) {
            throw new WalletValidationException("ETH custody source address is not configured");
        }
        BigInteger valueWei;
        try {
            valueWei = withdrawal.amount().multiply(WEI_PER_ETH).toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new WalletValidationException("ETH withdrawal amount cannot be represented in wei");
        }
        var data = ethereumRpcClient.transactionData(sourceAddress, withdrawal.destinationAddress(), valueWei);
        BigInteger feeWei = data.gasLimit().multiply(data.maxFeePerGas());
        if (ethereumRpcClient.getBalanceWei(sourceAddress).compareTo(valueWei.add(feeWei)) < 0) {
            throw new WalletValidationException("ETH custody source address has insufficient balance for withdrawal and maximum network fee");
        }
        String payload = "{\"type\":\"EIP-1559\",\"chainId\":\"" + data.chainId()
            + "\",\"nonce\":\"" + data.nonce()
            + "\",\"gasLimit\":\"" + data.gasLimit()
            + "\",\"maxFeePerGas\":\"" + data.maxFeePerGas()
            + "\",\"maxPriorityFeePerGas\":\"" + data.maxPriorityFeePerGas()
            + "\",\"to\":\"" + withdrawal.destinationAddress()
            + "\",\"value\":\"" + valueWei + "\",\"data\":\"0x\"}";
        return new UnsignedTransactionDraft(
            "EIP1559_JSON", "eth-eip1559-v1", payload, null, data.nonce().longValueExact(), null,
            new BigDecimal(feeWei).divide(WEI_PER_ETH),
            "{\"feeTier\":\"" + feeTier + "\",\"source\":\"" + sourceAddress + "\"}"
        );
    }
}
