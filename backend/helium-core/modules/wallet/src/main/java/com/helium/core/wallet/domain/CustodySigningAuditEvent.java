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
@Table(name = "wallet_custody_signing_audit")
public class CustodySigningAuditEvent {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false)
    private UUID withdrawalId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "custody_provider", nullable = false, updatable = false, length = 80)
    private String custodyProvider;

    @Column(name = "key_alias", nullable = false, updatable = false, length = 160)
    private String keyAlias;

    @Column(name = "key_version", nullable = false, updatable = false, length = 80)
    private String keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, updatable = false, length = 40)
    private SigningAlgorithm algorithm;

    @Column(name = "latency_ms", nullable = false, updatable = false)
    private long latencyMs;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "error_reason", updatable = false, length = 500)
    private String errorReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CustodySigningAuditEvent() {}

    private CustodySigningAuditEvent(
        UUID withdrawalId,
        String assetCode,
        String custodyProvider,
        String keyAlias,
        String keyVersion,
        SigningAlgorithm algorithm,
        long latencyMs,
        boolean success,
        String errorReason,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.custodyProvider = BlockchainNetwork.requireText(custodyProvider, "custodyProvider", 80);
        this.keyAlias = BlockchainNetwork.requireText(keyAlias, "keyAlias", 160);
        this.keyVersion = BlockchainNetwork.requireText(keyVersion, "keyVersion", 80);
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.latencyMs = Math.max(0, latencyMs);
        this.success = success;
        this.errorReason = errorReason == null || errorReason.isBlank() ? null : BlockchainNetwork.requireText(errorReason, "errorReason", 500);
        this.occurredAt = Objects.requireNonNull(now, "now");
    }

    public static CustodySigningAuditEvent record(
        UUID withdrawalId,
        String assetCode,
        String custodyProvider,
        String keyAlias,
        String keyVersion,
        SigningAlgorithm algorithm,
        long latencyMs,
        boolean success,
        String errorReason,
        Instant now
    ) {
        return new CustodySigningAuditEvent(withdrawalId, assetCode, custodyProvider, keyAlias, keyVersion, algorithm,
            latencyMs, success, errorReason, now);
    }

    public UUID withdrawalId() { return withdrawalId; }
    public String assetCode() { return assetCode; }
    public String custodyProvider() { return custodyProvider; }
    public String keyAlias() { return keyAlias; }
    public String keyVersion() { return keyVersion; }
    public SigningAlgorithm algorithm() { return algorithm; }
    public long latencyMs() { return latencyMs; }
    public boolean success() { return success; }
    public String errorReason() { return errorReason; }
    public Instant occurredAt() { return occurredAt; }
}
