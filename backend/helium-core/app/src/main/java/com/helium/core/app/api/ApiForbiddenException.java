package com.helium.core.app.api;

public class ApiForbiddenException extends RuntimeException {
    public ApiForbiddenException(String message) {
        super(message);
    }
}
