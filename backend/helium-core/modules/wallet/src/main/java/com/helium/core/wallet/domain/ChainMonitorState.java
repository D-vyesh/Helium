package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "wallet_chain_monitor_states")
public class ChainMonitorState {
    @Id
    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "last_observed_block_height", nullable = false)
    private long lastObservedBlockHeight;

    @Column(name = "last_confirmed_block_height", nullable = false)
    private long lastConfirmedBlockHeight;

    @Column(name = "reorg_checkpoint_block_height", nullable = false)
    private long reorgCheckpointBlockHeight;

    @Column(name = "scan_checkpoint_block_height", nullable = false)
    private long scanCheckpointBlockHeight;

    @Column(name = "last_successful_provider", length = 160)
    private String lastSuccessfulProvider;

    @Column(name = "last_observed_block_hash", length = 160)
    private String lastObservedBlockHash;

    @Column(name = "last_observed_parent_hash", length = 160)
    private String lastObservedParentHash;

    @Column(name = "deep_reorg_review_required", nullable = false)
    private boolean deepReorgReviewRequired;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ChainMonitorState() {
    }

    private ChainMonitorState(String networkCode, long blockHeight, long confirmedBlockHeight, long reorgCheckpointBlockHeight, Instant now) {
        if (blockHeight < 0) {
            throw new WalletValidationException("block height cannot be negative");
        }
        if (confirmedBlockHeight < 0 || confirmedBlockHeight > blockHeight) {
            throw new WalletValidationException("confirmed block height is invalid");
        }
        if (reorgCheckpointBlockHeight < 0 || reorgCheckpointBlockHeight > blockHeight) {
            throw new WalletValidationException("reorg checkpoint height is invalid");
        }
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.lastObservedBlockHeight = blockHeight;
        this.lastConfirmedBlockHeight = confirmedBlockHeight;
        this.reorgCheckpointBlockHeight = reorgCheckpointBlockHeight;
        this.scanCheckpointBlockHeight = blockHeight;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public static ChainMonitorState start(String networkCode, long blockHeight, Instant now) {
        return new ChainMonitorState(networkCode, blockHeight, blockHeight, blockHeight, now);
    }

    public void advanceTo(long blockHeight, long confirmedBlockHeight, long reorgCheckpointBlockHeight, Instant now) {
        if (blockHeight < lastObservedBlockHeight) {
            throw new WalletValidationException("chain monitor height cannot decrease");
        }
        if (confirmedBlockHeight < 0 || confirmedBlockHeight > blockHeight) {
            throw new WalletValidationException("confirmed block height is invalid");
        }
        if (reorgCheckpointBlockHeight < 0 || reorgCheckpointBlockHeight > blockHeight) {
            throw new WalletValidationException("reorg checkpoint height is invalid");
        }
        this.lastObservedBlockHeight = blockHeight;
        this.lastConfirmedBlockHeight = confirmedBlockHeight;
        this.reorgCheckpointBlockHeight = reorgCheckpointBlockHeight;
        this.scanCheckpointBlockHeight = blockHeight;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void recordSuccessfulScan(
        long scanCheckpointBlockHeight,
        String providerId,
        String blockHash,
        String parentHash,
        Instant now
    ) {
        if (scanCheckpointBlockHeight < 0 || scanCheckpointBlockHeight > lastObservedBlockHeight) {
            throw new WalletValidationException("scan checkpoint height is invalid");
        }
        this.scanCheckpointBlockHeight = scanCheckpointBlockHeight;
        this.lastSuccessfulProvider = providerId == null || providerId.isBlank()
            ? null
            : BlockchainNetwork.requireText(providerId, "providerId", 160);
        this.lastObservedBlockHash = blankToNull(blockHash);
        this.lastObservedParentHash = blankToNull(parentHash);
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void requireDeepReorgReview(Instant now) {
        this.deepReorgReviewRequired = true;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void advanceTo(long blockHeight, Instant now) {
        advanceTo(blockHeight, Math.min(lastConfirmedBlockHeight, blockHeight), Math.min(reorgCheckpointBlockHeight, blockHeight), now);
    }

    public String networkCode() {
        return networkCode;
    }

    public long lastObservedBlockHeight() {
        return lastObservedBlockHeight;
    }

    public long lastConfirmedBlockHeight() {
        return lastConfirmedBlockHeight;
    }

    public long reorgCheckpointBlockHeight() {
        return reorgCheckpointBlockHeight;
    }

    public long scanCheckpointBlockHeight() {
        return scanCheckpointBlockHeight;
    }

    public String lastSuccessfulProvider() {
        return lastSuccessfulProvider;
    }

    public String lastObservedBlockHash() {
        return lastObservedBlockHash;
    }

    public String lastObservedParentHash() {
        return lastObservedParentHash;
    }

    public boolean deepReorgReviewRequired() {
        return deepReorgReviewRequired;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : BlockchainNetwork.requireText(value, "blockHash", 160);
    }
}
