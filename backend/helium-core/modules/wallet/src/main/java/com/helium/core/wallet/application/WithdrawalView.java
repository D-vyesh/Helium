package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WithdrawalStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalView(
    UUID withdrawalId,
    String clientRequestId,
    UUID userId,
    String assetCode,
    String networkCode,
    BigDecimal amount,
    BigDecimal fee,
    WithdrawalStatus status,
    String broadcastTxHash
) {
}

