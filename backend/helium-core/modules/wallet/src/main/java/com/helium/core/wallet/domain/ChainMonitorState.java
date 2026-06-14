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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ChainMonitorState() {
    }

    private ChainMonitorState(String networkCode, long blockHeight, Instant now) {
        if (blockHeight < 0) {
            throw new WalletValidationException("block height cannot be negative");
        }
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.lastObservedBlockHeight = blockHeight;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public static ChainMonitorState start(String networkCode, long blockHeight, Instant now) {
        return new ChainMonitorState(networkCode, blockHeight, now);
    }

    public void advanceTo(long blockHeight, Instant now) {
        if (blockHeight < lastObservedBlockHeight) {
            throw new WalletValidationException("chain monitor height cannot decrease");
        }
        this.lastObservedBlockHeight = blockHeight;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public long lastObservedBlockHeight() {
        return lastObservedBlockHeight;
    }
}

