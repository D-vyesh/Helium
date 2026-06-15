package com.helium.core.authuser.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(SessionCachePort.class)
class NoopSessionCachePort implements SessionCachePort {
    @Override
    public Optional<SessionView> find(String tokenHash) {
        return Optional.empty();
    }

    @Override
    public void store(String tokenHash, SessionView session) {
    }

    @Override
    public void evict(String tokenHash) {
    }

    @Override
    public void revokeUser(UUID userId, Instant revokedAt) {
    }

    @Override
    public boolean isUserRevokedAfter(UUID userId, Instant sessionCreatedAt) {
        return false;
    }
}
