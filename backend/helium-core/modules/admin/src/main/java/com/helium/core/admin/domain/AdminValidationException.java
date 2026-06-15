package com.helium.core.admin.domain;

public class AdminValidationException extends RuntimeException {
    public AdminValidationException(String message) {
        super(message);
    }
}
