package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.SigningAlgorithm;
import java.time.Instant;
import java.util.UUID;

public record CustodyAuditAdminView(
    UUID withdrawalId,
    String assetCode,
    String custodyProvider,
    String keyAlias,
    String keyVersion,
    SigningAlgorithm algorithm,
    long latencyMs,
    boolean success,
    String errorReason,
    Instant occurredAt
) {}
