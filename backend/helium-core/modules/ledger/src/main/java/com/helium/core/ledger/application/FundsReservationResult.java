package com.helium.core.ledger.application;

import java.util.UUID;

public record FundsReservationResult(
    UUID transactionId,
    String idempotencyKey,
    boolean idempotentReplay
) {
}
