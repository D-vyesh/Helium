package com.helium.core.wallet.application;

import java.math.BigDecimal;

public record ReconciliationSnapshot(
    String assetCode,
    String networkCode,
    BigDecimal walletTotal,
    BigDecimal ledgerTotal,
    BigDecimal chainTotal,
    long discrepancyCount
) {
}
