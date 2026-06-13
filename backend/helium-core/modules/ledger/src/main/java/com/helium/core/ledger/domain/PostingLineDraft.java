package com.helium.core.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record PostingLineDraft(
    LedgerAccount account,
    PostingDirection direction,
    BigDecimal amount
) {
    public PostingLineDraft {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(direction, "direction");
        amount = requirePositiveAmount(amount);
        if (!account.isActive()) {
            throw new LedgerValidationException("posting account must be active");
        }
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new LedgerValidationException("posting amount must be positive");
        }
        return amount.stripTrailingZeros();
    }
}

