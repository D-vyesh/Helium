package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.DepositStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record DepositView(
    UUID depositId,
    UUID userId,
    String assetCode,
    String networkCode,
    BigDecimal amount,
    DepositStatus status,
    UUID ledgerTransactionId
) {
}

