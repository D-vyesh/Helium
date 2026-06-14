package com.helium.core.trading.domain;

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
@Table(name = "trading_order_history")
public class OrderAuditRecord {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private OrderStatus status;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 120)
    private String actorId;

    @Column(name = "details", nullable = false, updatable = false, length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected OrderAuditRecord() {
    }

    private OrderAuditRecord(UUID orderId, OrderStatus status, String actorId, String details, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");
        this.actorId = Market.requireText(actorId, "actorId", 120);
        this.details = Market.requireText(details, "details", 1000);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static OrderAuditRecord record(UUID orderId, OrderStatus status, String actorId, String details, Instant occurredAt) {
        return new OrderAuditRecord(orderId, status, actorId, details, occurredAt);
    }
}
