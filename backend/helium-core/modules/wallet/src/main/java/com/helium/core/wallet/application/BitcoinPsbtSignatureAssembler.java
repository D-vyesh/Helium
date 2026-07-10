package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.SigningAlgorithm;
import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class BitcoinPsbtSignatureAssembler implements TransactionSignatureAssembler {
    @Override
    public String assetCode() {
        return "BTC";
    }

    @Override
    public SigningRequest prepare(UnsignedTransaction unsignedTransaction, CustodyKey custodyKey) {
        if (custodyKey.algorithm() != SigningAlgorithm.BTC_PSBT) {
            throw new WalletValidationException("BTC custody key must use BTC_PSBT signing");
        }
        String psbt = unsignedTransaction.psbt();
        if (psbt == null || psbt.isBlank()) {
            throw new WalletValidationException("BTC unsigned transaction is missing its PSBT");
        }
        return new SigningRequest(
            "BTC",
            unsignedTransaction.withdrawalId(),
            psbt,
            CryptoEncoding.sha256Hex(psbt.getBytes(StandardCharsets.UTF_8)),
            Base64.getEncoder().encodeToString(psbt.getBytes(StandardCharsets.UTF_8)),
            custodyKey.keyAlias(),
            custodyKey.keyVersion(),
            custodyKey.algorithm(),
            "{\"format\":\"PSBT\"}"
        );
    }

    @Override
    public SignedTransactionDraft assemble(UnsignedTransaction unsignedTransaction, SigningResult signingResult, CustodyKey custodyKey) {
        String signedPsbt = signingResult.signedPayload();
        if (signedPsbt == null || signedPsbt.isBlank()) {
            throw new WalletValidationException("custody provider did not return a signed BTC PSBT");
        }
        return new SignedTransactionDraft(
            "SIGNED_PSBT",
            signedPsbt,
            CryptoEncoding.sha256Hex(unsignedTransaction.psbt().getBytes(StandardCharsets.UTF_8)),
            signingResult.signature(),
            custodyKey.algorithm()
        );
    }
}
