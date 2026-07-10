package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_withdrawal_confirmations")
public class WithdrawalConfirmation {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false, unique = true)
    private UUID withdrawalId;

    @Column(name = "tx_hash", nullable = false, length = 160)
    private String txHash;

    @Column(name = "confirmations", nullable = false)
    private int confirmations;

    @Column(name = "required_confirmations", nullable = false)
    private int requiredConfirmations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WithdrawalConfirmationStatus status;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WithdrawalConfirmation() {
    }

    private WithdrawalConfirmation(UUID withdrawalId, String txHash, int confirmations, int requiredConfirmations, Instant now) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.txHash = BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.requiredConfirmations = positive(requiredConfirmations, "requiredConfirmations");
        this.confirmations = Math.max(0, confirmations);
        this.status = this.confirmations >= this.requiredConfirmations
            ? WithdrawalConfirmationStatus.CONFIRMED
            : WithdrawalConfirmationStatus.CONFIRMING;
        this.firstSeenAt = Objects.requireNonNull(now, "now");
        this.lastCheckedAt = now;
        this.confirmedAt = this.status == WithdrawalConfirmationStatus.CONFIRMED ? now : null;
    }

    public static WithdrawalConfirmation start(UUID withdrawalId, String txHash, int confirmations, int requiredConfirmations, Instant now) {
        return new WithdrawalConfirmation(withdrawalId, txHash, confirmations, requiredConfirmations, now);
    }

    public boolean update(int currentConfirmations, int required, Instant now) {
        int sanitized = Math.max(0, currentConfirmations);
        boolean changed = this.confirmations != sanitized || this.requiredConfirmations != required;
        this.confirmations = sanitized;
        this.requiredConfirmations = positive(required, "requiredConfirmations");
        this.lastCheckedAt = Objects.requireNonNull(now, "now");
        this.failureReason = null;
        if (sanitized >= this.requiredConfirmations) {
            this.status = WithdrawalConfirmationStatus.CONFIRMED;
            if (this.confirmedAt == null) {
                this.confirmedAt = now;
                changed = true;
            }
        } else {
            this.status = WithdrawalConfirmationStatus.CONFIRMING;
        }
        return changed;
    }

    public void markReorgDetected(String reason, Instant now) {
        this.status = WithdrawalConfirmationStatus.REORG_DETECTED;
        this.failureReason = BlockchainNetwork.requireText(reason, "reason", 500);
        this.lastCheckedAt = Objects.requireNonNull(now, "now");
    }

    public void markFailed(String reason, Instant now) {
        this.status = WithdrawalConfirmationStatus.FAILED;
        this.failureReason = BlockchainNetwork.requireText(reason, "reason", 500);
        this.lastCheckedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() {
        return id;
    }

    public UUID withdrawalId() {
        return withdrawalId;
    }

    public String txHash() {
        return txHash;
    }

    public int confirmations() {
        return confirmations;
    }

    public int requiredConfirmations() {
        return requiredConfirmations;
    }

    public WithdrawalConfirmationStatus status() {
        return status;
    }

    public Instant lastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public String failureReason() {
        return failureReason;
    }

    private static int positive(int value, String field) {
        if (value <= 0) {
            throw new WalletValidationException(field + " must be positive");
        }
        return value;
    }
}
