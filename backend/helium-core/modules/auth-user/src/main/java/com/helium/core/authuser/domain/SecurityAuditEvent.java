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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "auth_security_audit_events")
public class SecurityAuditEvent {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 60)
    private SecurityAuditEventType eventType;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 120)
    private String actorId;

    @Column(name = "ip_address", nullable = false, updatable = false, length = 64)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, updatable = false, length = 500)
    private String userAgent;

    @Column(name = "details", nullable = false, updatable = false, length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SecurityAuditEvent() {
    }

    private SecurityAuditEvent(
        SecurityAuditEventType eventType,
        UUID userId,
        UUID sessionId,
        String actorId,
        String ipAddress,
        String userAgent,
        String details,
        Instant occurredAt
    ) {
        this.id = UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.userId = userId;
        this.sessionId = sessionId;
        this.actorId = requireText(actorId, "actorId", 120);
        this.ipAddress = requireText(ipAddress, "ipAddress", 64);
        this.userAgent = requireText(userAgent, "userAgent", 500);
        this.details = requireText(details, "details", 1000);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static SecurityAuditEvent record(
        SecurityAuditEventType eventType,
        UUID userId,
        UUID sessionId,
        String actorId,
        String ipAddress,
        String userAgent,
        String details,
        Instant occurredAt
    ) {
        return new SecurityAuditEvent(eventType, userId, sessionId, actorId, ipAddress, userAgent, details, occurredAt);
    }

    private static String requireText(String value, String field, int maximumLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank() || text.length() > maximumLength) {
            throw new AuthValidationException(field + " is invalid");
        }
        return text;
    }
}
