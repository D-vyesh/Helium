package com.helium.core.app.api;

public class ApiUnauthorizedException extends RuntimeException {
    public ApiUnauthorizedException(String message) {
        super(message);
    }
}
