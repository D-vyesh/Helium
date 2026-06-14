package com.helium.core.wallet.domain;

public class WalletValidationException extends RuntimeException {
    public WalletValidationException(String message) {
        super(message);
    }
}

