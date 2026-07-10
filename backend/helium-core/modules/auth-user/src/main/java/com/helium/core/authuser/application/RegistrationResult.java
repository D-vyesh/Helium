package com.helium.core.authuser.application;

import java.util.UUID;

/**
 * Result of a successful registration.
 * The emailVerificationToken is NEVER returned to the client — it is sent via email only.
 * The field is package-private so the EmailService can use it internally.
 */
public record RegistrationResult(UUID userId) {
}
