package com.helium.core.wallet.application;

import java.time.Instant;

public record BlockchainTransactionObservation(
    String network,
    String transactionId,
    String providerId,
    boolean observed,
    boolean canonical,
    Long blockHeightOrSlot,
    String blockHash,
    Long confirmations,
    String executionStatus,
    Instant observedAt
) {
    public String agreementKey() {
        return observed + "|" + canonical + "|" + blockHeightOrSlot + "|" + blockHash + "|" + executionStatus;
    }
}
