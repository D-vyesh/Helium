package com.helium.core.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;

public record ObserveWithdrawalCommand(
    UUID withdrawalId,
    String txHash,
    BigDecimal amount,
    String destinationAddress,
    String destinationMemo,
    int confirmations
) {
}
