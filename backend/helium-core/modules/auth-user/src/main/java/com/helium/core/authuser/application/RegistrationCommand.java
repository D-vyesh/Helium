package com.helium.core.authuser.application;

public record RegistrationCommand(
    String email,
    String displayName,
    String password,
    SecurityContextData securityContext
) {
}
