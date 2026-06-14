package com.helium.core.matching.domain;

public enum MatchingOrderStatus {
    ACTIVE,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    EXPIRED,
    REJECTED;

    public boolean matchable() {
        return this == ACTIVE || this == PARTIALLY_FILLED;
    }
}
