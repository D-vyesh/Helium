package com.helium.core.wallet.domain;

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
@Table(name = "wallet_audit_events")
public class WalletAuditEvent {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 60)
    private WalletAuditEventType eventType;

    @Column(name = "aggregate_id", updatable = false)
    private UUID aggregateId;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 120)
    private String actorId;

    @Column(name = "details", nullable = false, updatable = false, length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected WalletAuditEvent() {
    }

    private WalletAuditEvent(WalletAuditEventType eventType, UUID aggregateId, String actorId, String details, Instant now) {
        this.id = UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.aggregateId = aggregateId;
        this.actorId = requireText(actorId, "actorId", 120);
        this.details = requireText(details, "details", 1000);
        this.occurredAt = Objects.requireNonNull(now, "now");
    }

    public static WalletAuditEvent record(
        WalletAuditEventType eventType,
        UUID aggregateId,
        String actorId,
        String details,
        Instant now
    ) {
        return new WalletAuditEvent(eventType, aggregateId, actorId, details, now);
    }

    private static String requireText(String value, String field, int maximumLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) {
            throw new WalletValidationException(field + " is required");
        }
        if (text.length() > maximumLength) {
            throw new WalletValidationException(field + " is too long");
        }
        return text;
    }
}

