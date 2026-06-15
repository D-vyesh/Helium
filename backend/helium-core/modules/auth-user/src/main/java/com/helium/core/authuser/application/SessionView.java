package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionView(UUID sessionId, UUID userId, Instant createdAt, Instant expiresAt, Set<Role> roles) {
}
