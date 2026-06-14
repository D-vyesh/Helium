package com.helium.core.wallet.application;

import java.math.BigDecimal;

public record DetectDepositCommand(
    String networkCode,
    String address,
    String txHash,
    int outputIndex,
    BigDecimal amount
) {
}
