package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_blockchain_broadcasts")
public class BlockchainBroadcast {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false)
    private UUID withdrawalId;

    @Column(name = "signed_transaction_id", nullable = false, updatable = false)
    private UUID signedTransactionId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "tx_hash", length = 160)
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BlockchainBroadcastStatus status;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Column(name = "node_id", nullable = false, length = 160)
    private String nodeId;

    @Column(name = "fee_paid", precision = 38, scale = 18)
    private BigDecimal feePaid;

    @Column(name = "rpc_latency_ms")
    private Long rpcLatencyMs;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "error_reason", length = 500)
    private String errorReason;

    @Column(name = "broadcasted_at")
    private Instant broadcastedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BlockchainBroadcast() {
    }

    private BlockchainBroadcast(
        UUID withdrawalId,
        UUID signedTransactionId,
        String assetCode,
        String networkCode,
        int attemptNumber,
        BlockchainBroadcastStatus status,
        String txHash,
        String provider,
        String nodeId,
        BigDecimal feePaid,
        Long rpcLatencyMs,
        String rawResponse,
        String errorReason,
        Instant broadcastedAt,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.signedTransactionId = Objects.requireNonNull(signedTransactionId, "signedTransactionId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.attemptNumber = attemptNumber;
        this.status = Objects.requireNonNull(status, "status");
        this.txHash = txHash == null ? null : BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.provider = BlockchainNetwork.requireText(provider, "provider", 80);
        this.nodeId = BlockchainNetwork.requireText(nodeId, "nodeId", 160);
        this.feePaid = feePaid == null ? null : BlockchainNetwork.requireNonNegative(feePaid, "feePaid");
        this.rpcLatencyMs = rpcLatencyMs;
        this.rawResponse = rawResponse;
        this.errorReason = errorReason == null ? null : BlockchainNetwork.requireText(errorReason, "errorReason", 500);
        this.broadcastedAt = broadcastedAt;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static BlockchainBroadcast broadcasted(
        UUID withdrawalId,
        UUID signedTransactionId,
        String assetCode,
        String networkCode,
        int attemptNumber,
        String txHash,
        String provider,
        String nodeId,
        BigDecimal feePaid,
        long rpcLatencyMs,
        String rawResponse,
        Instant now
    ) {
        return new BlockchainBroadcast(withdrawalId, signedTransactionId, assetCode, networkCode, attemptNumber,
            BlockchainBroadcastStatus.BROADCASTED, txHash, provider, nodeId, feePaid, rpcLatencyMs, rawResponse, null, now, now);
    }

    public static BlockchainBroadcast failed(
        UUID withdrawalId,
        UUID signedTransactionId,
        String assetCode,
        String networkCode,
        int attemptNumber,
        String provider,
        String nodeId,
        Long rpcLatencyMs,
        String errorReason,
        Instant now
    ) {
        return new BlockchainBroadcast(withdrawalId, signedTransactionId, assetCode, networkCode, attemptNumber,
            BlockchainBroadcastStatus.FAILED, null, provider, nodeId, null, rpcLatencyMs, null, errorReason, null, now);
    }

    public UUID id() {
        return id;
    }

    public UUID withdrawalId() {
        return withdrawalId;
    }

    public UUID signedTransactionId() {
        return signedTransactionId;
    }

    public String assetCode() {
        return assetCode;
    }

    public String networkCode() {
        return networkCode;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public String txHash() {
        return txHash;
    }

    public BlockchainBroadcastStatus status() {
        return status;
    }

    public String provider() {
        return provider;
    }

    public String nodeId() {
        return nodeId;
    }

    public BigDecimal feePaid() {
        return feePaid;
    }

    public Long rpcLatencyMs() {
        return rpcLatencyMs;
    }

    public String rawResponse() {
        return rawResponse;
    }

    public String errorReason() {
        return errorReason;
    }

    public Instant broadcastedAt() {
        return broadcastedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
