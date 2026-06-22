package com.helium.core.app.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.authuser.application.SessionCachePort;
import com.helium.core.authuser.application.SessionView;
import com.helium.core.authuser.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RedisSessionCacheAdapter implements SessionCachePort {
    private static final String SESSION_PREFIX = "helium:session:";
    private static final String USER_REVOKED_PREFIX = "helium:session-revoked:";
    private final Optional<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSessionCacheAdapter(ObjectProvider<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = Optional.ofNullable(redisTemplate.getIfAvailable());
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SessionView> find(String tokenHash) {
        try {
            return redisTemplate
                .map(template -> template.opsForValue().get(SESSION_PREFIX + tokenHash))
                .flatMap(this::deserialize);
        } catch (DataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void store(String tokenHash, SessionView session) {
        try {
            redisTemplate.ifPresent(template -> template.opsForValue().set(
                SESSION_PREFIX + tokenHash,
                serialize(session),
                Duration.between(Instant.now(), session.expiresAt()).abs()
            ));
        } catch (DataAccessException exception) {
            // Redis is an optional cache; the database-backed session remains authoritative.
        }
    }

    @Override
    public void evict(String tokenHash) {
        try {
            redisTemplate.ifPresent(template -> template.delete(SESSION_PREFIX + tokenHash));
        } catch (DataAccessException exception) {
            // Best-effort cache eviction.
        }
    }

    @Override
    public void revokeUser(UUID userId, Instant revokedAt) {
        try {
            redisTemplate.ifPresent(template -> {
                template.opsForValue().set(USER_REVOKED_PREFIX + userId, revokedAt.toString(), Duration.ofDays(45));
                template.convertAndSend("helium:sessions:revoked", userId + ":" + revokedAt);
            });
        } catch (DataAccessException exception) {
            // Best-effort cross-node revocation hint; database state still revokes sessions.
        }
    }

    @Override
    public boolean isUserRevokedAfter(UUID userId, Instant sessionCreatedAt) {
        try {
            return redisTemplate
                .map(template -> template.opsForValue().get(USER_REVOKED_PREFIX + userId))
                .flatMap(value -> {
                    try {
                        return Optional.of(Instant.parse(value));
                    } catch (RuntimeException exception) {
                        return Optional.empty();
                    }
                })
                .map(revokedAt -> !revokedAt.isBefore(sessionCreatedAt))
                .orElse(false);
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private String serialize(SessionView session) {
        try {
            return objectMapper.writeValueAsString(new CachedSession(
                session.sessionId(),
                session.userId(),
                session.createdAt(),
                session.expiresAt(),
                session.roles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet())
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize session cache entry", exception);
        }
    }

    private Optional<SessionView> deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            CachedSession cached = objectMapper.readValue(payload, CachedSession.class);
            Set<Role> roles = cached.roles().stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
            return Optional.of(new SessionView(
                cached.sessionId(),
                cached.userId(),
                cached.createdAt(),
                cached.expiresAt(),
                roles
            ));
        } catch (RuntimeException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private record CachedSession(
        UUID sessionId,
        UUID userId,
        Instant createdAt,
        Instant expiresAt,
        Set<String> roles
    ) {}
}
