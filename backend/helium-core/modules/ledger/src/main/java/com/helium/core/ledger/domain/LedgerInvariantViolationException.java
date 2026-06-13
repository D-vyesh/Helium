package com.helium.core.ledger.domain;

public class LedgerInvariantViolationException extends RuntimeException {
    public LedgerInvariantViolationException(String message) {
        super(message);
    }
}

