package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.PostingDirection;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PostingLineCommand(
    UUID accountId,
    PostingDirection direction,
    BigDecimal amount
) {
    public PostingLineCommand {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        amount = amount.stripTrailingZeros();
    }
}

