package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "auth_mfa_methods")
public class MfaMethod {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private MfaType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private MfaStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    protected MfaMethod() {
    }

    private MfaMethod(UUID userId, MfaType type, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.type = Objects.requireNonNull(type, "type");
        this.status = MfaStatus.PENDING;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static MfaMethod pendingTotp(UUID userId, Instant now) {
        return new MfaMethod(userId, MfaType.TOTP, now);
    }

    public void enable(Instant now) {
        if (this.status == MfaStatus.ENABLED) return;
        this.status = MfaStatus.ENABLED;
        this.enabledAt = Objects.requireNonNull(now, "now");
    }

    public void disable(Instant now) {
        this.status = MfaStatus.DISABLED;
        this.disabledAt = Objects.requireNonNull(now, "now");
    }

    public UUID userId() { return userId; }
    public MfaType type() { return type; }
    public MfaStatus status() { return status; }
}
