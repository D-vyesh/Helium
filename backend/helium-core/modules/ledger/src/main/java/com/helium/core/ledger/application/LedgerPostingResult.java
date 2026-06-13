package com.helium.core.ledger.application;

import java.util.UUID;

public record LedgerPostingResult(
    UUID transactionId,
    String idempotencyKey,
    boolean idempotentReplay
) {
}

