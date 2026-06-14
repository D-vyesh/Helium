package com.helium.core.trading.domain;

public class TradingValidationException extends RuntimeException {
    public TradingValidationException(String message) {
        super(message);
    }
}
