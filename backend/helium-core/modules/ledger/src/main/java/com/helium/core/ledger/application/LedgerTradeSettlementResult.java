package com.helium.core.ledger.application;

import java.util.UUID;

public record LedgerTradeSettlementResult(
    UUID transactionId,
    String idempotencyKey,
    boolean idempotentReplay
) {
}
