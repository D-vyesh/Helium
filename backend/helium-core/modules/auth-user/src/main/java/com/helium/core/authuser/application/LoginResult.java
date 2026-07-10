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
    LoginFailureReason failureReason,
    /** Non-null when failureReason == MFA_REQUIRED. Used to complete the TOTP challenge. */
    String mfaSessionToken
) {
    public static LoginResult failed(LoginFailureReason reason) {
        return new LoginResult(false, null, null, null, Set.of(), reason, null);
    }

    public static LoginResult mfaRequired(UUID userId, String mfaSessionToken) {
        return new LoginResult(false, userId, null, null, Set.of(), LoginFailureReason.MFA_REQUIRED, mfaSessionToken);
    }

    public static LoginResult succeeded(UUID userId, String sessionToken, Instant expiresAt, Set<Role> roles) {
        return new LoginResult(true, userId, sessionToken, expiresAt, Set.copyOf(roles), null, null);
    }
}
