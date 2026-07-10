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
@Table(name = "wallet_withdrawal_queue_transitions")
public class WithdrawalQueueTransition {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "queue_id", nullable = false, updatable = false)
    private UUID queueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private WithdrawalQueueStatus status;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 120)
    private String actorId;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected WithdrawalQueueTransition() {
    }

    private WithdrawalQueueTransition(UUID queueId, WithdrawalQueueStatus status, String actorId, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.queueId = Objects.requireNonNull(queueId, "queueId");
        this.status = Objects.requireNonNull(status, "status");
        this.actorId = BlockchainNetwork.requireText(actorId, "actorId", 120);
        this.reason = BlockchainNetwork.requireText(reason, "reason", 500);
        this.occurredAt = Objects.requireNonNull(now, "now");
    }

    public static WithdrawalQueueTransition record(UUID queueId, WithdrawalQueueStatus status, String actorId, String reason, Instant now) {
        return new WithdrawalQueueTransition(queueId, status, actorId, reason, now);
    }
}
