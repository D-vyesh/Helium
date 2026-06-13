package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record LoginResult(
    boolean authenticated,
    UUID userId,
    String sessionToken,
    Instant expiresAt,
    Set<Role> roles,
    LoginFailureReason failureReason
) {
    public static LoginResult failed(LoginFailureReason reason) {
        return new LoginResult(false, null, null, null, Set.of(), reason);
    }

    public static LoginResult succeeded(UUID userId, String sessionToken, Instant expiresAt, Set<Role> roles) {
        return new LoginResult(true, userId, sessionToken, expiresAt, Set.copyOf(roles), null);
    }
}
