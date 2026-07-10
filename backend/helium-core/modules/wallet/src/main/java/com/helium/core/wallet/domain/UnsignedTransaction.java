package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable builder output. It is not a signature, transaction hash, or broadcast record. */
@Entity
@Table(name = "wallet_unsigned_transactions")
public class UnsignedTransaction {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false, unique = true)
    private UUID withdrawalId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "format", nullable = false, updatable = false, length = 40)
    private String format;

    @Column(name = "builder_version", nullable = false, updatable = false, length = 40)
    private String builderVersion;

    @Column(name = "serialized_payload", nullable = false, updatable = false, columnDefinition = "text")
    private String serializedPayload;

    @Column(name = "psbt", columnDefinition = "text", updatable = false)
    private String psbt;

    @Column(name = "nonce", updatable = false)
    private Long nonce;

    @Column(name = "recent_blockhash", length = 128, updatable = false)
    private String recentBlockhash;

    @Column(name = "fee", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal fee;

    @Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "text")
    private String metadata;

    @Column(name = "built_at", nullable = false, updatable = false)
    private Instant builtAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UnsignedTransaction() {}

    private UnsignedTransaction(
        UUID withdrawalId, String assetCode, String networkCode, String format, String builderVersion,
        String serializedPayload, String psbt, Long nonce, String recentBlockhash, BigDecimal fee, String metadata, Instant now
    ) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.format = BlockchainNetwork.requireText(format, "format", 40);
        this.builderVersion = BlockchainNetwork.requireText(builderVersion, "builderVersion", 40);
        this.serializedPayload = BlockchainNetwork.requireText(serializedPayload, "serializedPayload", 2_000_000);
        this.psbt = psbt;
        this.nonce = nonce;
        this.recentBlockhash = recentBlockhash;
        this.fee = BlockchainNetwork.requireNonNegative(fee, "fee");
        this.metadata = BlockchainNetwork.requireText(metadata, "metadata", 2_000_000);
        this.builtAt = Objects.requireNonNull(now, "now");
    }

    public static UnsignedTransaction built(
        UUID withdrawalId, String assetCode, String networkCode, String format, String builderVersion,
        String serializedPayload, String psbt, Long nonce, String recentBlockhash, BigDecimal fee, String metadata, Instant now
    ) {
        return new UnsignedTransaction(withdrawalId, assetCode, networkCode, format, builderVersion,
            serializedPayload, psbt, nonce, recentBlockhash, fee, metadata, now);
    }

    public UUID id() { return id; }
    public UUID withdrawalId() { return withdrawalId; }
    public String assetCode() { return assetCode; }
    public String networkCode() { return networkCode; }
    public String format() { return format; }
    public String builderVersion() { return builderVersion; }
    public String serializedPayload() { return serializedPayload; }
    public String psbt() { return psbt; }
    public Long nonce() { return nonce; }
    public String recentBlockhash() { return recentBlockhash; }
    public BigDecimal fee() { return fee; }
    public String metadata() { return metadata; }
    public Instant builtAt() { return builtAt; }
}
