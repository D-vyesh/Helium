package com.helium.core.app.api;

import com.helium.core.authuser.application.AccountStatusPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;
    private final ApiKeyProperties properties;
    private final AccountStatusPort accountStatusPort;
    private final Clock clock;

    public ApiKeyService(
        JdbcTemplate jdbcTemplate,
        ApiKeyProperties properties,
        AccountStatusPort accountStatusPort,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.accountStatusPort = accountStatusPort;
        this.clock = clock;
    }

    @Transactional
    public CreatedApiKey create(UUID userId, String label, List<String> ipAllowlist, String actorId) {
        if (!accountStatusPort.isActive(userId)) {
            throw new ApiForbiddenException("active account is required");
        }
        String keyId = "hk_" + token(12);
        String secret = token(32);
        jdbcTemplate.update("""
            insert into api_keys (key_id, user_id, label, secret_hash, ip_allowlist, created_at)
            values (?, ?, ?, ?, ?, ?)
            """, keyId, userId, label, hashSecret(secret, properties.currentPepper()), String.join(",", ipAllowlist), Timestamp.from(Instant.now(clock)));
        audit(keyId, userId, actorId, "CREATED");
        return new CreatedApiKey(keyId, keyId + "." + secret);
    }

    @Transactional
    public void revoke(UUID userId, String keyId, String actorId) {
        int updated = jdbcTemplate.update("""
            update api_keys
               set revoked_at = ?, updated_at = ?
             where key_id = ?
               and user_id = ?
               and revoked_at is null
            """, Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)), keyId, userId);
        if (updated == 0) {
            throw new ApiForbiddenException("api key is not revocable by current actor");
        }
        audit(keyId, userId, actorId, "REVOKED");
    }

    public Optional<ApiKeyAuthentication> authenticate(String presentedKey, String ipAddress) {
        int separator = presentedKey == null ? -1 : presentedKey.indexOf('.');
        if (separator <= 0 || separator == presentedKey.length() - 1) {
            return Optional.empty();
        }
        String keyId = presentedKey.substring(0, separator);
        String secret = presentedKey.substring(separator + 1);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                select user_id, secret_hash, coalesce(ip_allowlist, '') as ip_allowlist
                  from api_keys
                 where key_id = ?
                   and revoked_at is null
                """, (rs, rowNum) -> {
                String storedHash = rs.getString("secret_hash");
                if (!matchesSecret(secret, storedHash)) {
                    return null;
                }
                UUID userId = rs.getObject("user_id", UUID.class);
                if (!accountStatusPort.isActive(userId)) {
                    return null;
                }
                String ipAllowlist = rs.getString("ip_allowlist");
                if (!ipAllowed(ipAddress, ipAllowlist)) {
                    return null;
                }
                return new ApiKeyAuthentication(keyId, userId, secret);
            }, keyId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public boolean validSignature(String secret, String canonicalRequest, String providedSignature) {
        byte[] expected = hmac(secret.getBytes(StandardCharsets.UTF_8), canonicalRequest);
        byte[] provided = decodeHex(providedSignature);
        return provided.length == expected.length && MessageDigest.isEqual(expected, provided);
    }

    private boolean matchesSecret(String secret, String storedHash) {
        return constantEquals(hashSecret(secret, properties.currentPepper()), storedHash)
            || (properties.previousPepper() != null && constantEquals(hashSecret(secret, properties.previousPepper()), storedHash));
    }

    private String hashSecret(String secret, String pepper) {
        return HexFormat.of().formatHex(hmac(pepper.getBytes(StandardCharsets.UTF_8), secret));
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private boolean constantEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] decodeHex(String signature) {
        try {
            return HexFormat.of().parseHex(signature);
        } catch (RuntimeException exception) {
            return new byte[0];
        }
    }

    private boolean ipAllowed(String ipAddress, String ipAllowlist) {
        if (ipAllowlist == null || ipAllowlist.isBlank()) {
            return true;
        }
        return Arrays.stream(ipAllowlist.split(","))
            .map(String::trim)
            .anyMatch(ipAddress::equals);
    }

    private String token(int bytes) {
        byte[] value = new byte[bytes];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void audit(String keyId, UUID userId, String actorId, String action) {
        jdbcTemplate.update("""
            insert into api_key_audit_events (id, key_id, user_id, actor_id, action, created_at)
            values (?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), keyId, userId, actorId, action, Timestamp.from(Instant.now(clock)));
    }

    public record CreatedApiKey(String keyId, String secret) {}

    public record ApiKeyAuthentication(String keyId, UUID userId, String secret) {}
}
