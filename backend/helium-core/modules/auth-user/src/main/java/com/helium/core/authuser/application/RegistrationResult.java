package com.helium.core.authuser.application;

import java.util.UUID;

public record RegistrationResult(UUID userId, String emailVerificationToken) {
}
