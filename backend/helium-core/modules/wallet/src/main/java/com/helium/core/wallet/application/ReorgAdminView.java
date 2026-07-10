package com.helium.core.wallet.application;

import java.time.Instant;
import java.util.UUID;

public record ReorgAdminView(
    UUID id,
    UUID withdrawalId,
    String assetCode,
    String networkCode,
    String txHash,
    int previousConfirmations,
    int currentConfirmations,
    String reason,
    boolean manualReviewRequired,
    Instant detectedAt
) {
}
