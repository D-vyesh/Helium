package com.helium.core.authuser.domain;

public class AuthValidationException extends RuntimeException {
    public AuthValidationException(String message) {
        super(message);
    }
}
