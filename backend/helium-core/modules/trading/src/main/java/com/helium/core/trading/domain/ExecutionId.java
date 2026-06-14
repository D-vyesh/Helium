package com.helium.core.trading.domain;

public record ExecutionId(String value) {
    public ExecutionId {
        value = Market.requireText(value, "executionId", 120);
    }
}
