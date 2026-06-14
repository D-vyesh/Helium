package com.helium.core.marketdata.domain;

import java.math.BigDecimal;

public final class MarketDataNumbers {
    private MarketDataNumbers() {
    }

    public static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new MarketDataValidationException(field + " must be positive");
        }
        return value.stripTrailingZeros();
    }

    public static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new MarketDataValidationException(field + " cannot be negative");
        }
        return value.stripTrailingZeros();
    }
}
