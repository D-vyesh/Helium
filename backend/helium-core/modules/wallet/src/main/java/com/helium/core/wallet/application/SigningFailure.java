package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainNetwork;
import java.time.Instant;
import java.util.UUID;

public record SigningFailure(
    UUID withdrawalId,
    String provider,
    String keyAlias,
    String reason,
    Instant occurredAt
) {
    public SigningFailure {
        provider = BlockchainNetwork.requireText(provider, "provider", 80);
        keyAlias = BlockchainNetwork.requireText(keyAlias, "keyAlias", 160);
        reason = BlockchainNetwork.requireText(reason, "reason", 500);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
