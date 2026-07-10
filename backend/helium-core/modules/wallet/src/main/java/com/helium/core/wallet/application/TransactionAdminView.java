package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WithdrawalConfirmationStatus;
import java.time.Instant;
import java.util.UUID;

public record TransactionAdminView(
    UUID withdrawalId,
    String assetCode,
    String networkCode,
    String txHash,
    int confirmations,
    int requiredConfirmations,
    WithdrawalConfirmationStatus status,
    Instant lastCheckedAt,
    Instant confirmedAt,
    String failureReason
) {
}
