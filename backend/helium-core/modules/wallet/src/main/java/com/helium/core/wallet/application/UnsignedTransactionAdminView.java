package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Administrative metadata only. Serialized unsigned payloads stay within the custody workflow. */
public record UnsignedTransactionAdminView(
    String assetCode,
    String networkCode,
    WithdrawalQueueStatus queueStatus,
    String builderType,
    String builderVersion,
    BigDecimal fee,
    Long nonce,
    String recentBlockhash,
    boolean psbtAvailable,
    int serializedPayloadBytes,
    Instant builtAt,
    int buildAttempts,
    Instant nextBuildAttemptAt,
    String lastBuildError
) {}
