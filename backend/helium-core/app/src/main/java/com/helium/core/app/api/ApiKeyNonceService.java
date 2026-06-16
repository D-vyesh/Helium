package com.helium.core.app.api;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Redis-backed nonce validation for API key replay attack protection.
 * Uses SET NX with TTL matching the signature window to ensure each nonce is used only once.
 * Falls back to PostgreSQL nonce table when Redis is unavailable.
 */
@Service
public class ApiKeyNonceService {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyNonceService.class);
    private static final String NONCE_PREFIX = "helium:nonce:";

    private final Optional<StringRedisTemplate> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ApiKeyProperties properties;
    private final Clock clock;

    public ApiKeyNonceService(
        ObjectProvider<StringRedisTemplate> redisTemplate,
        JdbcTemplate jdbcTemplate,
        ApiKeyProperties properties,
        Clock clock
    ) {
        this.redisTemplate = Optional.ofNullable(redisTemplate.getIfAvailable());
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Validate and consume a nonce. Returns true if the nonce is valid (not previously used).
     * Returns false if the nonce has already been used (replay attack).
     */
    public boolean validateAndConsume(String keyId, String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        return redisTemplate
            .map(template -> validateWithRedis(template, keyId, nonce))
            .orElseGet(() -> validateWithPostgres(keyId, nonce));
    }

    private boolean validateWithRedis(StringRedisTemplate template, String keyId, String nonce) {
        String redisKey = NONCE_PREFIX + keyId + ":" + nonce;
        Duration ttl = properties.nonceTtl();
        Boolean wasSet = template.opsForValue().setIfAbsent(redisKey, "1", ttl);
        return Boolean.TRUE.equals(wasSet);
    }

    private boolean validateWithPostgres(String keyId, String nonce) {
        try {
            Instant expiresAt = Instant.now(clock).plus(properties.nonceTtl());
            int inserted = jdbcTemplate.update("""
                insert into api_key_nonces (key_id, nonce, expires_at, created_at)
                values (?, ?, ?, ?)
                on conflict (key_id, nonce) do nothing
                """, keyId, nonce, Timestamp.from(expiresAt), Timestamp.from(Instant.now(clock)));
            return inserted > 0;
        } catch (RuntimeException exception) {
            log.warn("PostgreSQL nonce validation failed: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * Cleanup expired nonces from PostgreSQL. Runs every 10 minutes.
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanupExpiredNonces() {
        try {
            int deleted = jdbcTemplate.update("delete from api_key_nonces where expires_at < ?",
                Timestamp.from(Instant.now(clock)));
            if (deleted > 0) {
                log.info("Cleaned up {} expired API key nonces", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("Nonce cleanup failed: {}", exception.getMessage());
        }
    }
}
