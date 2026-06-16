package com.helium.core.wallet.infrastructure.blockchain;

import java.math.BigDecimal;

public record DetectedDeposit(
    String networkId,
    String txHash,
    int outputIndex,
    BigDecimal amount,
    String asset,
    String destinationAddress
) {}
