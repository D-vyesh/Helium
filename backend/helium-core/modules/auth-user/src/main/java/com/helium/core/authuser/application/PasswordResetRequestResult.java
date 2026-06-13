package com.helium.core.authuser.application;

public record PasswordResetRequestResult(String rawToken) {
    public static PasswordResetRequestResult accepted(String rawToken) {
        return new PasswordResetRequestResult(rawToken);
    }
}
