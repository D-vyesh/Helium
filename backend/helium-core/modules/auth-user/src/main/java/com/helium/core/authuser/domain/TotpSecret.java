package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores the encrypted TOTP secret for a user.
 * The secret is AES-256-GCM encrypted at rest; the raw secret is never persisted.
 */
@Entity
@Table(
    name = "auth_totp_secrets",
    uniqueConstraints = @UniqueConstraint(name = "uk_auth_totp_secrets_user", columnNames = "user_id")
)
public class TotpSecret {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** AES-256-GCM encrypted TOTP secret, Base64-encoded (iv:ciphertext). */
    @Column(name = "encrypted_secret", nullable = false, length = 512)
    private String encryptedSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TotpSecret() {}

    private TotpSecret(UUID userId, String encryptedSecret, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.encryptedSecret = Objects.requireNonNull(encryptedSecret, "encryptedSecret");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static TotpSecret create(UUID userId, String encryptedSecret, Instant now) {
        return new TotpSecret(userId, encryptedSecret, now);
    }

    public void updateSecret(String encryptedSecret, Instant now) {
        this.encryptedSecret = Objects.requireNonNull(encryptedSecret, "encryptedSecret");
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String encryptedSecret() { return encryptedSecret; }
}
