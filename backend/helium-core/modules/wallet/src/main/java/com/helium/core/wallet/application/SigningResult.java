package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainNetwork;
import java.time.Duration;

public record SigningResult(
    String custodyProvider,
    String signature,
    String signedPayload,
    Duration latency
) {
    public SigningResult {
        custodyProvider = BlockchainNetwork.requireText(custodyProvider, "custodyProvider", 80);
        signature = BlockchainNetwork.requireText(signature, "signature", 2_000_000);
        signedPayload = signedPayload == null || signedPayload.isBlank()
            ? signature
            : BlockchainNetwork.requireText(signedPayload, "signedPayload", 2_000_000);
        latency = latency == null ? Duration.ZERO : latency;
    }
}
