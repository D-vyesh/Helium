package com.helium.core.trading.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record LimitPrice(BigDecimal value) {
    public LimitPrice {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new TradingValidationException("limit price must be positive");
        }
        value = value.stripTrailingZeros();
    }
}
