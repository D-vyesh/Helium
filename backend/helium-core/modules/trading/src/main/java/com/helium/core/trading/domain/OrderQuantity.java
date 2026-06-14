package com.helium.core.trading.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderQuantity(BigDecimal value) {
    public OrderQuantity {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new TradingValidationException("order quantity must be positive");
        }
        value = value.stripTrailingZeros();
    }
}
