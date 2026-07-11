package com.helium.core.wallet.infrastructure.blockchain;

public record CanonicalBlockReference(
    String network,
    long height,
    String blockHash,
    String parentHash
) {}
