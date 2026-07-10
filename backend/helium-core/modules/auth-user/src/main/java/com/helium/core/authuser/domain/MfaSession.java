package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary session issued after password verification when MFA is required.
 * The user must complete the MFA challenge within the TTL to obtain a full session.
 */
@Entity
@Table(name = "auth_mfa_sessions")
public class MfaSession {

    private static final Duration MFA_SESSION_TTL = Duration.ofMinutes(5);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected MfaSession() {}

    private MfaSession(UUID userId, String tokenHash, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.createdAt = now;
        this.expiresAt = now.plus(MFA_SESSION_TTL);
    }

    public static MfaSession create(UUID userId, String tokenHash, Instant now) {
        return new MfaSession(userId, tokenHash, now);
    }

    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new AuthValidationException("MFA session has already been used");
        }
        if (!expiresAt.isAfter(now)) {
            throw new AuthValidationException("MFA session has expired");
        }
        this.consumedAt = now;
    }

    public boolean isValid(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
}
