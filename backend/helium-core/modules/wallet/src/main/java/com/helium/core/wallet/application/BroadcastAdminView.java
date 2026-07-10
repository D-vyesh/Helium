package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainBroadcastStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BroadcastAdminView(
    UUID id,
    UUID withdrawalId,
    String assetCode,
    String networkCode,
    int attemptNumber,
    String txHash,
    BlockchainBroadcastStatus status,
    String provider,
    String nodeId,
    BigDecimal feePaid,
    Long rpcLatencyMs,
    String errorReason,
    Instant broadcastedAt,
    Instant createdAt
) {
}
