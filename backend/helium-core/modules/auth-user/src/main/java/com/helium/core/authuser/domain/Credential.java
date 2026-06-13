package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "auth_credentials",
    uniqueConstraints = @UniqueConstraint(name = "uk_auth_credentials_user", columnNames = "user_id")
)
public class Credential {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Credential() {
    }

    private Credential(UUID userId, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.passwordHash = requireHash(passwordHash);
        this.passwordChangedAt = Objects.requireNonNull(now, "now");
    }

    public static Credential create(UUID userId, String passwordHash, Instant now) {
        return new Credential(userId, passwordHash, now);
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = requireHash(passwordHash);
        this.passwordChangedAt = Objects.requireNonNull(now, "now");
        this.mustChangePassword = false;
    }

    public UUID userId() {
        return userId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant passwordChangedAt() {
        return passwordChangedAt;
    }

    private static String requireHash(String value) {
        if (Objects.requireNonNull(value, "passwordHash").isBlank()) {
            throw new AuthValidationException("passwordHash is required");
        }
        return value;
    }
}
