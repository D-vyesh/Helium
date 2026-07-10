package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_withdrawal_reorg_events")
public class WithdrawalReorgEvent {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false)
    private UUID withdrawalId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "tx_hash", nullable = false, updatable = false, length = 160)
    private String txHash;

    @Column(name = "previous_confirmations", nullable = false, updatable = false)
    private int previousConfirmations;

    @Column(name = "current_confirmations", nullable = false, updatable = false)
    private int currentConfirmations;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "manual_review_required", nullable = false, updatable = false)
    private boolean manualReviewRequired;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected WithdrawalReorgEvent() {
    }

    private WithdrawalReorgEvent(
        UUID withdrawalId,
        String assetCode,
        String networkCode,
        String txHash,
        int previousConfirmations,
        int currentConfirmations,
        String reason,
        boolean manualReviewRequired,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.txHash = BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.previousConfirmations = Math.max(0, previousConfirmations);
        this.currentConfirmations = Math.max(0, currentConfirmations);
        this.reason = BlockchainNetwork.requireText(reason, "reason", 500);
        this.manualReviewRequired = manualReviewRequired;
        this.detectedAt = Objects.requireNonNull(now, "now");
    }

    public static WithdrawalReorgEvent detected(
        UUID withdrawalId,
        String assetCode,
        String networkCode,
        String txHash,
        int previousConfirmations,
        int currentConfirmations,
        String reason,
        boolean manualReviewRequired,
        Instant now
    ) {
        return new WithdrawalReorgEvent(withdrawalId, assetCode, networkCode, txHash, previousConfirmations,
            currentConfirmations, reason, manualReviewRequired, now);
    }

    public UUID id() {
        return id;
    }

    public UUID withdrawalId() {
        return withdrawalId;
    }

    public String assetCode() {
        return assetCode;
    }

    public String networkCode() {
        return networkCode;
    }

    public String txHash() {
        return txHash;
    }

    public int previousConfirmations() {
        return previousConfirmations;
    }

    public int currentConfirmations() {
        return currentConfirmations;
    }

    public String reason() {
        return reason;
    }

    public boolean manualReviewRequired() {
        return manualReviewRequired;
    }

    public Instant detectedAt() {
        return detectedAt;
    }
}
