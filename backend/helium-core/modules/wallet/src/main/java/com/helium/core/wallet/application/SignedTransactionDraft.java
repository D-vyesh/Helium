package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.SigningAlgorithm;

public record SignedTransactionDraft(
    String format,
    String serializedPayload,
    String signingDigest,
    String signature,
    SigningAlgorithm algorithm
) {
    public SignedTransactionDraft {
        format = BlockchainNetwork.requireText(format, "format", 40);
        serializedPayload = BlockchainNetwork.requireText(serializedPayload, "serializedPayload", 2_000_000);
        signingDigest = BlockchainNetwork.requireText(signingDigest, "signingDigest", 160);
        signature = BlockchainNetwork.requireText(signature, "signature", 2_000_000);
        java.util.Objects.requireNonNull(algorithm, "algorithm");
    }
}
