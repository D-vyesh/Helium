package com.helium.core.matching.domain;

import java.math.BigDecimal;
import java.util.Objects;

final class MatchingNumbers {
    private MatchingNumbers() {
    }

    static BigDecimal positive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() <= 0) {
            throw new MatchingValidationException(field + " must be positive");
        }
        return value.stripTrailingZeros();
    }

    static BigDecimal nonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new MatchingValidationException(field + " cannot be negative");
        }
        return value.stripTrailingZeros();
    }
}
