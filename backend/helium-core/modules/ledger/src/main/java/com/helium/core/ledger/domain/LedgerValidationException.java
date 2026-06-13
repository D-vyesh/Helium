package com.helium.core.ledger.domain;

public class LedgerValidationException extends RuntimeException {
    public LedgerValidationException(String message) {
        super(message);
    }
}

