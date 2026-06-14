package com.helium.core.wallet.application;

import java.math.BigDecimal;

public record RegisterNetworkCommand(
    String networkCode,
    String assetCode,
    String displayName,
    int requiredConfirmations,
    boolean depositEnabled,
    boolean withdrawalEnabled,
    BigDecimal minimumWithdrawal,
    BigDecimal withdrawalFee
) {
}
