package com.helium.core.trading.domain;

public class TradingInvariantViolationException extends RuntimeException {
    public TradingInvariantViolationException(String message) {
        super(message);
    }
}
