package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.blockchain.BitcoinRpcClient;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Builds a real Bitcoin Core-funded, unsigned native-SegWit PSBT. */
@Component
public class BitcoinTransactionBuilder implements UnsignedTransactionBuilder {
    private static final String BUILDER_VERSION = "btc-core-psbt-v1";
    private final BitcoinRpcClient bitcoinRpcClient;

    public BitcoinTransactionBuilder(BitcoinRpcClient bitcoinRpcClient) {
        this.bitcoinRpcClient = bitcoinRpcClient;
    }

    @Override
    public String assetCode() {
        return "BTC";
    }

    @Override
    public UnsignedTransactionDraft build(Withdrawal withdrawal, FeeTier feeTier) {
        if (!"BTC".equals(withdrawal.networkCode())) {
            throw new WalletValidationException("BTC builder requires the BTC network");
        }
        int targetBlocks = switch (feeTier) {
            case FAST -> 1;
            case MEDIUM -> 3;
            case SLOW -> 6;
        };
        BigDecimal feeRate = bitcoinRpcClient.estimateFeeBtcPerKilobyte(targetBlocks);
        long lockTime = bitcoinRpcClient.getBlockCount();
        BitcoinRpcClient.FundedPsbt psbt = bitcoinRpcClient.createFundedPsbt(
            withdrawal.destinationAddress(), withdrawal.amount(), feeRate, lockTime
        );
        return new UnsignedTransactionDraft(
            "PSBT",
            BUILDER_VERSION,
            psbt.psbt(),
            psbt.psbt(),
            null,
            null,
            psbt.fee(),
            "{\"feeRateBtcPerKvB\":\"" + feeRate.toPlainString()
                + "\",\"changePosition\":" + psbt.changePosition()
                + ",\"lockTime\":" + psbt.lockTime()
                + ",\"replaceByFee\":true}"
        );
    }
}
