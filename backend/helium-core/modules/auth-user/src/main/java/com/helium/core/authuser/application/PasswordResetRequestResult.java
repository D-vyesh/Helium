package com.helium.core.authuser.application;

/**
 * Result of a password reset request.
 * The token is NEVER returned to the client — it is sent via email only.
 */
public record PasswordResetRequestResult(boolean requestAccepted) {
    public static PasswordResetRequestResult accepted() {
        return new PasswordResetRequestResult(true);
    }
}
