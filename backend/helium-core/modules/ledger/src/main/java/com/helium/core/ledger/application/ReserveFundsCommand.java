package com.helium.core.ledger.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ReserveFundsCommand(
    UUID userId,
    String assetCode,
    BigDecimal amount,
    String businessReference,
    String idempotencyKey,
    String actorId,
    String reason
) {
    public ReserveFundsCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(amount, "amount");
        assetCode = requireText(assetCode, "assetCode").toUpperCase();
        businessReference = requireText(businessReference, "businessReference");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        actorId = requireText(actorId, "actorId");
        reason = requireText(reason, "reason");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        amount = amount.stripTrailingZeros();
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
