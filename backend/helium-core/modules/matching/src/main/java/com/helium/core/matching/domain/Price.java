package com.helium.core.matching.domain;

import java.math.BigDecimal;

public record Price(BigDecimal value) {
    public Price {
        value = MatchingNumbers.positive(value, "price");
    }
}
