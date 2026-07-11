package com.helium.core.wallet.application;

import java.util.List;

public record BlockchainObservationConsensus(
    BlockchainConsensusStatus status,
    List<BlockchainTransactionObservation> observations,
    long confirmations,
    String reason
) {
    public boolean agreed() {
        return status == BlockchainConsensusStatus.AGREED;
    }
}
