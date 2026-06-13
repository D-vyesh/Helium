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
@Table(name = "auth_role_grants")
public class RoleGrant {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 40)
    private Role role;

    @Column(name = "granted_by", nullable = false, updatable = false)
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RoleGrant() {
    }

    private RoleGrant(UUID userId, Role role, UUID grantedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.role = Objects.requireNonNull(role, "role");
        this.grantedBy = Objects.requireNonNull(grantedBy, "grantedBy");
        this.grantedAt = Objects.requireNonNull(now, "now");
    }

    public static RoleGrant grant(UUID userId, Role role, UUID grantedBy, Instant now) {
        return new RoleGrant(userId, role, grantedBy, now);
    }

    public void revoke(UUID actorId, Instant now) {
        if (revokedAt == null) {
            revokedBy = Objects.requireNonNull(actorId, "actorId");
            revokedAt = Objects.requireNonNull(now, "now");
        }
    }

    public UUID userId() {
        return userId;
    }

    public Role role() {
        return role;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
