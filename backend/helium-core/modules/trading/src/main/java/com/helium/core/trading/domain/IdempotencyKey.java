package com.helium.core.trading.domain;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        value = Market.requireText(value, "idempotencyKey", 120);
    }
}
