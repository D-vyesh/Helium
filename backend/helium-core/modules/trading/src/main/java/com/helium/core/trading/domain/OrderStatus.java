package com.helium.core.trading.domain;

public enum OrderStatus {
    RECEIVED,
    VALIDATED,
    FUNDS_RESERVED,
    SENT_TO_MATCHING,
    STOP_PENDING,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_REQUESTED,
    CANCELLED,
    EXPIRED,
    REJECTED,
    SUSPICIOUS;

    public boolean terminal() {
        return this == FILLED || this == CANCELLED || this == EXPIRED || this == REJECTED;
    }
}
