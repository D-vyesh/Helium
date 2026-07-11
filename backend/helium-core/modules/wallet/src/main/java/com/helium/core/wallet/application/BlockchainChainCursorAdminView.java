package com.helium.core.wallet.application;

import java.time.Instant;

public record BlockchainChainCursorAdminView(
    String networkCode,
    long lastObservedBlockHeight,
    long lastConfirmedBlockHeight,
    long reorgCheckpointBlockHeight,
    long scanCheckpointBlockHeight,
    String lastSuccessfulProvider,
    String lastObservedBlockHash,
    String lastObservedParentHash,
    boolean deepReorgReviewRequired,
    Instant updatedAt
) {}
