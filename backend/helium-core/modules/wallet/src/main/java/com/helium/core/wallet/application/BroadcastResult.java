package com.helium.core.wallet.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record BroadcastResult(
    UUID withdrawalId,
    UUID signedTransactionId,
    String assetCode,
    String networkCode,
    String txHash,
    String provider,
    String nodeId,
    BigDecimal feePaid,
    Duration rpcLatency,
    String rawResponse,
    Instant broadcastedAt
) {
}
