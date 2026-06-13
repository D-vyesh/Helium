package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "auth_login_attempt_throttles",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_auth_login_attempt_throttles_subject_source",
        columnNames = {"subject_hash", "source_hash"}
    )
)
public class LoginAttemptThrottle {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_hash", nullable = false, updatable = false, length = 64)
    private String subjectHash;

    @Column(name = "source_hash", nullable = false, updatable = false, length = 64)
    private String sourceHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LoginAttemptThrottle() {
    }

    private LoginAttemptThrottle(String subjectHash, String sourceHash, Instant now) {
        this.id = UUID.randomUUID();
        this.subjectHash = requireHash(subjectHash);
        this.sourceHash = requireHash(sourceHash);
        this.windowStartedAt = Objects.requireNonNull(now, "now");
    }

    public static LoginAttemptThrottle create(String subjectHash, String sourceHash, Instant now) {
        return new LoginAttemptThrottle(subjectHash, sourceHash, now);
    }

    public boolean recordFailure(int threshold, Duration window, Duration blockDuration, Instant now) {
        if (!windowStartedAt.plus(window).isAfter(now)) {
            failedAttempts = 0;
            windowStartedAt = now;
            blockedUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= threshold) {
            blockedUntil = now.plus(blockDuration);
        }
        return isBlocked(now);
    }

    public boolean isBlocked(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    private static String requireHash(String value) {
        String hash = Objects.requireNonNull(value, "hash");
        if (hash.length() != 64) {
            throw new AuthValidationException("hash is invalid");
        }
        return hash;
    }
}
