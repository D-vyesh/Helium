package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RefreshTokenRotationResult(
    UUID userId,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    Set<Role> roles
) {
    public RefreshTokenRotationResult {
        roles = Set.copyOf(roles);
    }
}
