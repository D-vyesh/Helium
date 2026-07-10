package com.helium.core.authuser.application;

public record RegistrationCommand(
    String email,
    String displayName,
    String password,
    SecurityContextData securityContext,
    String verificationBaseUrl
) {
    /** Convenience constructor for callers that don't supply a base URL. */
    public RegistrationCommand(String email, String displayName, String password, SecurityContextData securityContext) {
        this(email, displayName, password, securityContext, "http://localhost:3000");
    }
}
