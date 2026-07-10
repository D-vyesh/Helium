package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single-use backup code for TOTP recovery.
 * The code is stored as a SHA-256 hash; the raw code is shown to the user once.
 */
@Entity
@Table(name = "auth_totp_backup_codes")
public class TotpBackupCode {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected TotpBackupCode() {}

    private TotpBackupCode(UUID userId, String codeHash, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.createdAt = now;
    }

    public static TotpBackupCode create(UUID userId, String codeHash, Instant now) {
        return new TotpBackupCode(userId, codeHash, now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void consume(Instant now) {
        if (usedAt != null) {
            throw new AuthValidationException("backup code has already been used");
        }
        this.usedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String codeHash() { return codeHash; }
    public Instant usedAt() { return usedAt; }
}
