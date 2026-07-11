package com.helium.core.wallet.domain;

import com.helium.core.wallet.application.BlockchainConsensusStatus;
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
@Table(name = "blockchain_canonical_blocks")
public class BlockchainCanonicalBlock {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "network", nullable = false, updatable = false, length = 40)
    private String network;

    @Column(name = "height", nullable = false, updatable = false)
    private long height;

    @Column(name = "block_hash", nullable = false, updatable = false, length = 160)
    private String blockHash;

    @Column(name = "parent_hash", updatable = false, length = 160)
    private String parentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_consensus", nullable = false, length = 30)
    private BlockchainConsensusStatus providerConsensus;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    protected BlockchainCanonicalBlock() {
    }

    private BlockchainCanonicalBlock(
        String network,
        long height,
        String blockHash,
        String parentHash,
        BlockchainConsensusStatus providerConsensus,
        Instant now
    ) {
        if (height < 0) {
            throw new WalletValidationException("block height cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.network = BlockchainNetwork.normalizeNetworkCode(network);
        this.height = height;
        this.blockHash = BlockchainNetwork.requireText(blockHash, "blockHash", 160);
        this.parentHash = parentHash == null || parentHash.isBlank() ? null : BlockchainNetwork.requireText(parentHash, "parentHash", 160);
        this.providerConsensus = Objects.requireNonNull(providerConsensus, "providerConsensus");
        this.observedAt = Objects.requireNonNull(now, "now");
    }

    public static BlockchainCanonicalBlock observe(
        String network,
        long height,
        String blockHash,
        String parentHash,
        BlockchainConsensusStatus providerConsensus,
        Instant now
    ) {
        return new BlockchainCanonicalBlock(network, height, blockHash, parentHash, providerConsensus, now);
    }

    public UUID id() { return id; }
    public String network() { return network; }
    public long height() { return height; }
    public String blockHash() { return blockHash; }
    public String parentHash() { return parentHash; }
    public BlockchainConsensusStatus providerConsensus() { return providerConsensus; }
    public Instant observedAt() { return observedAt; }
}
