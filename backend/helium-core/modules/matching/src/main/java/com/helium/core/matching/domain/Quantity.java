package com.helium.core.matching.domain;

import java.math.BigDecimal;

public record Quantity(BigDecimal value) {
    public Quantity {
        value = MatchingNumbers.positive(value, "quantity");
    }
}
