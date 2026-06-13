package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "auth_email_verification_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uk_auth_email_verification_tokens_hash", columnNames = "token_hash")
)
public class EmailVerificationToken {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailVerificationToken() {
    }

    private EmailVerificationToken(UUID userId, String tokenHash, Duration lifetime, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = requireHash(tokenHash);
        this.createdAt = Objects.requireNonNull(now, "now");
        this.expiresAt = now.plus(Objects.requireNonNull(lifetime, "lifetime"));
    }

    public static EmailVerificationToken issue(UUID userId, String tokenHash, Duration lifetime, Instant now) {
        return new EmailVerificationToken(userId, tokenHash, lifetime, now);
    }

    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new AuthValidationException("email verification token was already consumed");
        }
        if (!expiresAt.isAfter(now)) {
            throw new AuthValidationException("email verification token has expired");
        }
        consumedAt = now;
    }

    public void invalidate(Instant now) {
        if (consumedAt == null) {
            consumedAt = Objects.requireNonNull(now, "now");
        }
    }

    public UUID userId() {
        return userId;
    }

    private static String requireHash(String value) {
        String hash = Objects.requireNonNull(value, "tokenHash");
        if (hash.length() != 64) {
            throw new AuthValidationException("tokenHash is invalid");
        }
        return hash;
    }
}
