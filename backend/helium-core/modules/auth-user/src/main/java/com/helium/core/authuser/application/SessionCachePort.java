package com.helium.core.authuser.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionCachePort {
    Optional<SessionView> find(String tokenHash);

    void store(String tokenHash, SessionView session);

    void evict(String tokenHash);

    void revokeUser(UUID userId, Instant revokedAt);

    boolean isUserRevokedAfter(UUID userId, Instant sessionCreatedAt);
}
