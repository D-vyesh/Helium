package com.helium.core.ledger.application;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceSnapshotView(
    UUID accountId,
    String assetCode,
    BigDecimal currentBalance
) {
}

