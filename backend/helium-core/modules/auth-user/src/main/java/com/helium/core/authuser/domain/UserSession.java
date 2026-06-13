package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "auth_user_sessions",
    uniqueConstraints = @UniqueConstraint(name = "uk_auth_user_sessions_token_hash", columnNames = "token_hash")
)
public class UserSession {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SessionStatus status;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 160)
    private String revocationReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserSession() {
    }

    private UserSession(UUID userId, String tokenHash, String ipAddress, String userAgent, Duration lifetime, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = requireText(tokenHash, "tokenHash", 64);
        this.ipAddress = requireText(ipAddress, "ipAddress", 64);
        this.userAgent = requireText(userAgent, "userAgent", 500);
        this.status = SessionStatus.ACTIVE;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.expiresAt = now.plus(Objects.requireNonNull(lifetime, "lifetime"));
    }

    public static UserSession create(
        UUID userId,
        String tokenHash,
        String ipAddress,
        String userAgent,
        Duration lifetime,
        Instant now
    ) {
        return new UserSession(userId, tokenHash, ipAddress, userAgent, lifetime, now);
    }

    public boolean isActive(Instant now) {
        if (status == SessionStatus.ACTIVE && !expiresAt.isAfter(now)) {
            status = SessionStatus.EXPIRED;
        }
        return status == SessionStatus.ACTIVE;
    }

    public void touch(Instant now) {
        if (!isActive(now)) {
            throw new AuthValidationException("session is not active");
        }
        lastSeenAt = now;
    }

    public void revoke(String reason, Instant now) {
        if (status == SessionStatus.ACTIVE) {
            status = SessionStatus.REVOKED;
            revokedAt = now;
            revocationReason = requireText(reason, "reason", 160);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public SessionStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    private static String requireText(String value, String field, int maximumLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank() || text.length() > maximumLength) {
            throw new AuthValidationException(field + " is invalid");
        }
        return text;
    }
}
