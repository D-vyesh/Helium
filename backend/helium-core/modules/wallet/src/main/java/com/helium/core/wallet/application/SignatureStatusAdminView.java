package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import java.time.Instant;
import java.util.UUID;

public record SignatureStatusAdminView(
    UUID withdrawalId,
    WithdrawalQueueStatus queueStatus,
    int signAttempts,
    Instant nextSignAttemptAt,
    String lastError,
    boolean signedTransactionAvailable,
    Instant signedAt
) {}
