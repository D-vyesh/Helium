package com.helium.core.admin.domain;

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
@Table(name = "admin_audit_events")
public class AdminAuditEvent {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 80)
    private AdminAuditAction action;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 120)
    private String actorId;

    @Column(name = "target_type", nullable = false, updatable = false, length = 80)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false, length = 160)
    private String targetId;

    @Column(name = "details", nullable = false, updatable = false, length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AdminAuditEvent() {
    }

    private AdminAuditEvent(AdminAuditAction action, String actorId, String targetType, String targetId, String details, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.action = Objects.requireNonNull(action, "action");
        this.actorId = requireText(actorId, "actorId", 120);
        this.targetType = requireText(targetType, "targetType", 80);
        this.targetId = requireText(targetId, "targetId", 160);
        this.details = requireText(details, "details", 1000);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AdminAuditEvent record(AdminAuditAction action, String actorId, String targetType, String targetId, String details, Instant occurredAt) {
        return new AdminAuditEvent(action, actorId, targetType, targetId, details, occurredAt);
    }

    public UUID id() {
        return id;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new AdminValidationException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new AdminValidationException(field + " is too long");
        }
        return normalized;
    }
}
