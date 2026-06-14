package com.helium.core.trading.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record FeeAmount(BigDecimal value) {
    public FeeAmount {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new TradingValidationException("fee amount cannot be negative");
        }
        value = value.stripTrailingZeros();
    }
}
