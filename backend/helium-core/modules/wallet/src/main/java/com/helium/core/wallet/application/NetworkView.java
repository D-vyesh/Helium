package com.helium.core.wallet.application;

import java.math.BigDecimal;

public record NetworkView(
    String networkCode,
    String assetCode,
    int requiredConfirmations,
    boolean depositEnabled,
    boolean withdrawalEnabled,
    BigDecimal minimumWithdrawal,
    BigDecimal withdrawalFee
) {
}

