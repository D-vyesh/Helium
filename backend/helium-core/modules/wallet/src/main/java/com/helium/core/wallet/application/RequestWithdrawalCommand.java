package com.helium.core.wallet.application;

import java.math.BigDecimal;

public record RequestWithdrawalCommand(
    String clientRequestId,
    String assetCode,
    String networkCode,
    String destinationAddress,
    String destinationMemo,
    BigDecimal amount
) {
}
