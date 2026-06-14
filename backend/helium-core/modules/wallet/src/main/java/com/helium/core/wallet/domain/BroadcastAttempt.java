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
@Table(name = "wallet_broadcast_attempts")
public class BroadcastAttempt {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false)
    private UUID withdrawalId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "tx_hash", nullable = false, updatable = false, length = 160)
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private BroadcastAttemptStatus status;

    @Column(name = "recorded_by", nullable = false, updatable = false, length = 120)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected BroadcastAttempt() {
    }

    private BroadcastAttempt(UUID withdrawalId, int attemptNumber, String txHash, String actorId, Instant now) {
        if (attemptNumber <= 0) {
            throw new WalletValidationException("attempt number must be positive");
        }
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.attemptNumber = attemptNumber;
        this.txHash = BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.status = BroadcastAttemptStatus.RECORDED;
        this.recordedBy = BlockchainNetwork.requireText(actorId, "actorId", 120);
        this.recordedAt = Objects.requireNonNull(now, "now");
    }

    public static BroadcastAttempt recorded(UUID withdrawalId, int attemptNumber, String txHash, String actorId, Instant now) {
        return new BroadcastAttempt(withdrawalId, attemptNumber, txHash, actorId, now);
    }
}

