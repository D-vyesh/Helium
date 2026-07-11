package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/** Durable operational queue state for a withdrawal. Financial settlement remains on Withdrawal. */
@Entity
@Table(name = "wallet_withdrawal_queue")
public class WithdrawalQueueItem {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false, unique = true)
    private UUID withdrawalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private WithdrawalQueueStatus status;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "build_attempts", nullable = false)
    private int buildAttempts;

    @Column(name = "next_build_attempt_at")
    private Instant nextBuildAttemptAt;

    @Column(name = "sign_attempts", nullable = false)
    private int signAttempts;

    @Column(name = "next_sign_attempt_at")
    private Instant nextSignAttemptAt;

    @Column(name = "broadcast_attempts", nullable = false)
    private int broadcastAttempts;

    @Column(name = "next_broadcast_attempt_at")
    private Instant nextBroadcastAttemptAt;

    @Column(name = "confirmation_failures", nullable = false)
    private int confirmationFailures;

    @Column(name = "next_confirmation_attempt_at")
    private Instant nextConfirmationAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WithdrawalQueueItem() {
    }

    private WithdrawalQueueItem(UUID withdrawalId, Instant now) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.status = WithdrawalQueueStatus.REQUESTED;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static WithdrawalQueueItem enqueue(UUID withdrawalId, Instant now) {
        return new WithdrawalQueueItem(withdrawalId, now);
    }

    public void transitionTo(WithdrawalQueueStatus nextStatus, String reason, Instant now) {
        WithdrawalQueueStatus next = Objects.requireNonNull(nextStatus, "nextStatus");
        if (next == status) {
            return;
        }
        if (!allowedNextStatuses(status).contains(next)) {
            throw new WalletValidationException("withdrawal queue cannot transition from " + status + " to " + next);
        }
        this.status = next;
        this.lastError = next == WithdrawalQueueStatus.FAILED
            || next == WithdrawalQueueStatus.BUILD_FAILED
            || next == WithdrawalQueueStatus.SIGN_FAILED
            || next == WithdrawalQueueStatus.BROADCAST_FAILED
            || next == WithdrawalQueueStatus.CONFIRMATION_FAILED
            || next == WithdrawalQueueStatus.REORG_DETECTED
            || next == WithdrawalQueueStatus.CHAIN_REVIEW_REQUIRED
            ? BlockchainNetwork.requireText(reason, "failureReason", 500)
            : null;
        if (next != WithdrawalQueueStatus.BUILD_FAILED) {
            this.nextBuildAttemptAt = null;
        }
        if (next != WithdrawalQueueStatus.SIGN_FAILED) {
            this.nextSignAttemptAt = null;
        }
        if (next != WithdrawalQueueStatus.BROADCAST_FAILED) {
            this.nextBroadcastAttemptAt = null;
        }
        if (next != WithdrawalQueueStatus.CONFIRMATION_FAILED) {
            this.nextConfirmationAttemptAt = null;
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void recordBuildFailure(String reason, Instant nextAttemptAt, Instant now) {
        if (status != WithdrawalQueueStatus.BUILDING_TRANSACTION) {
            throw new WalletValidationException("withdrawal queue must be BUILDING_TRANSACTION to record a build failure");
        }
        transitionTo(WithdrawalQueueStatus.BUILD_FAILED, reason, now);
        this.buildAttempts++;
        this.nextBuildAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public void recordSignFailure(String reason, Instant nextAttemptAt, Instant now) {
        if (status != WithdrawalQueueStatus.SIGNING) {
            throw new WalletValidationException("withdrawal queue must be SIGNING to record a signing failure");
        }
        transitionTo(WithdrawalQueueStatus.SIGN_FAILED, reason, now);
        this.signAttempts++;
        this.nextSignAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public void recordBroadcastFailure(String reason, Instant nextAttemptAt, Instant now) {
        if (status != WithdrawalQueueStatus.BROADCASTING) {
            throw new WalletValidationException("withdrawal queue must be BROADCASTING to record a broadcast failure");
        }
        transitionTo(WithdrawalQueueStatus.BROADCAST_FAILED, reason, now);
        this.broadcastAttempts++;
        this.nextBroadcastAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public void recordConfirmationFailure(String reason, Instant nextAttemptAt, Instant now) {
        if (status != WithdrawalQueueStatus.CONFIRMING) {
            throw new WalletValidationException("withdrawal queue must be CONFIRMING to record a confirmation failure");
        }
        transitionTo(WithdrawalQueueStatus.CONFIRMATION_FAILED, reason, now);
        this.confirmationFailures++;
        this.nextConfirmationAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public UUID id() {
        return id;
    }

    public UUID withdrawalId() {
        return withdrawalId;
    }

    public WithdrawalQueueStatus status() {
        return status;
    }

    public int buildAttempts() {
        return buildAttempts;
    }

    public Instant nextBuildAttemptAt() {
        return nextBuildAttemptAt;
    }

    public int signAttempts() {
        return signAttempts;
    }

    public Instant nextSignAttemptAt() {
        return nextSignAttemptAt;
    }

    public int broadcastAttempts() {
        return broadcastAttempts;
    }

    public Instant nextBroadcastAttemptAt() {
        return nextBroadcastAttemptAt;
    }

    public int confirmationFailures() {
        return confirmationFailures;
    }

    public Instant nextConfirmationAttemptAt() {
        return nextConfirmationAttemptAt;
    }

    public String lastError() {
        return lastError;
    }

    private static EnumSet<WithdrawalQueueStatus> allowedNextStatuses(WithdrawalQueueStatus current) {
        return switch (current) {
            case REQUESTED -> EnumSet.of(WithdrawalQueueStatus.VALIDATING, WithdrawalQueueStatus.CANCELLED, WithdrawalQueueStatus.FAILED);
            case VALIDATING -> EnumSet.of(WithdrawalQueueStatus.APPROVED, WithdrawalQueueStatus.CANCELLED, WithdrawalQueueStatus.FAILED);
            case APPROVED -> EnumSet.of(WithdrawalQueueStatus.WAITING_SIGN, WithdrawalQueueStatus.CANCELLED, WithdrawalQueueStatus.FAILED);
            case WAITING_SIGN -> EnumSet.of(WithdrawalQueueStatus.BUILDING_TRANSACTION, WithdrawalQueueStatus.CANCELLED, WithdrawalQueueStatus.FAILED);
            case BUILDING_TRANSACTION -> EnumSet.of(WithdrawalQueueStatus.TRANSACTION_BUILT, WithdrawalQueueStatus.BUILD_FAILED, WithdrawalQueueStatus.FAILED);
            case BUILD_FAILED -> EnumSet.of(WithdrawalQueueStatus.BUILDING_TRANSACTION, WithdrawalQueueStatus.FAILED, WithdrawalQueueStatus.CANCELLED);
            case TRANSACTION_BUILT -> EnumSet.of(WithdrawalQueueStatus.WAITING_SIGNER, WithdrawalQueueStatus.FAILED);
            case WAITING_SIGNER -> EnumSet.of(WithdrawalQueueStatus.SIGNING, WithdrawalQueueStatus.CANCELLED, WithdrawalQueueStatus.FAILED);
            case SIGNING -> EnumSet.of(WithdrawalQueueStatus.SIGNED, WithdrawalQueueStatus.SIGN_FAILED, WithdrawalQueueStatus.WAITING_SIGNER, WithdrawalQueueStatus.FAILED);
            case SIGN_FAILED -> EnumSet.of(WithdrawalQueueStatus.SIGNING, WithdrawalQueueStatus.FAILED, WithdrawalQueueStatus.CANCELLED);
            case SIGNED -> EnumSet.of(WithdrawalQueueStatus.WAITING_BROADCAST, WithdrawalQueueStatus.FAILED);
            case WAITING_BROADCAST -> EnumSet.of(WithdrawalQueueStatus.BROADCASTING, WithdrawalQueueStatus.FAILED, WithdrawalQueueStatus.CANCELLED);
            case BROADCASTING -> EnumSet.of(WithdrawalQueueStatus.BROADCASTED, WithdrawalQueueStatus.BROADCAST_FAILED, WithdrawalQueueStatus.FAILED);
            case BROADCAST_FAILED -> EnumSet.of(WithdrawalQueueStatus.BROADCASTING, WithdrawalQueueStatus.FAILED, WithdrawalQueueStatus.CANCELLED);
            case BROADCASTED -> EnumSet.of(WithdrawalQueueStatus.CONFIRMING, WithdrawalQueueStatus.PENDING_CONFIRMATIONS, WithdrawalQueueStatus.FAILED);
            case PENDING_CONFIRMATIONS -> EnumSet.of(WithdrawalQueueStatus.CONFIRMING, WithdrawalQueueStatus.CONFIRMED, WithdrawalQueueStatus.FAILED);
            case CONFIRMING -> EnumSet.of(WithdrawalQueueStatus.CONFIRMED, WithdrawalQueueStatus.CONFIRMATION_FAILED, WithdrawalQueueStatus.REORG_DETECTED, WithdrawalQueueStatus.CHAIN_REVIEW_REQUIRED, WithdrawalQueueStatus.FAILED);
            case CONFIRMATION_FAILED -> EnumSet.of(WithdrawalQueueStatus.CONFIRMING, WithdrawalQueueStatus.FAILED, WithdrawalQueueStatus.CANCELLED);
            case REORG_DETECTED -> EnumSet.of(WithdrawalQueueStatus.CONFIRMING, WithdrawalQueueStatus.CHAIN_REVIEW_REQUIRED, WithdrawalQueueStatus.FAILED);
            case CHAIN_REVIEW_REQUIRED -> EnumSet.of(WithdrawalQueueStatus.CONFIRMING, WithdrawalQueueStatus.FAILED);
            case CONFIRMED, FAILED, CANCELLED -> EnumSet.noneOf(WithdrawalQueueStatus.class);
        };
    }
}
