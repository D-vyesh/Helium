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
@Table(name = "wallet_signed_transactions")
public class SignedTransaction {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false, unique = true)
    private UUID withdrawalId;

    @Column(name = "unsigned_transaction_id", nullable = false, updatable = false)
    private UUID unsignedTransactionId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "format", nullable = false, updatable = false, length = 40)
    private String format;

    @Column(name = "serialized_payload", nullable = false, updatable = false, columnDefinition = "text")
    private String serializedPayload;

    @Column(name = "signing_digest", nullable = false, updatable = false, length = 160)
    private String signingDigest;

    @Column(name = "signature", nullable = false, updatable = false, columnDefinition = "text")
    private String signature;

    @Column(name = "custody_provider", nullable = false, updatable = false, length = 80)
    private String custodyProvider;

    @Column(name = "key_alias", nullable = false, updatable = false, length = 160)
    private String keyAlias;

    @Column(name = "key_version", nullable = false, updatable = false, length = 80)
    private String keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, updatable = false, length = 40)
    private SigningAlgorithm algorithm;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SignedTransaction() {}

    private SignedTransaction(
        UUID withdrawalId,
        UUID unsignedTransactionId,
        String assetCode,
        String networkCode,
        String format,
        String serializedPayload,
        String signingDigest,
        String signature,
        String custodyProvider,
        String keyAlias,
        String keyVersion,
        SigningAlgorithm algorithm,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.unsignedTransactionId = Objects.requireNonNull(unsignedTransactionId, "unsignedTransactionId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.format = BlockchainNetwork.requireText(format, "format", 40);
        this.serializedPayload = BlockchainNetwork.requireText(serializedPayload, "serializedPayload", 2_000_000);
        this.signingDigest = BlockchainNetwork.requireText(signingDigest, "signingDigest", 160);
        this.signature = BlockchainNetwork.requireText(signature, "signature", 2_000_000);
        this.custodyProvider = BlockchainNetwork.requireText(custodyProvider, "custodyProvider", 80);
        this.keyAlias = BlockchainNetwork.requireText(keyAlias, "keyAlias", 160);
        this.keyVersion = BlockchainNetwork.requireText(keyVersion, "keyVersion", 80);
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.signedAt = Objects.requireNonNull(now, "now");
    }

    public static SignedTransaction record(
        UUID withdrawalId,
        UUID unsignedTransactionId,
        String assetCode,
        String networkCode,
        String format,
        String serializedPayload,
        String signingDigest,
        String signature,
        String custodyProvider,
        String keyAlias,
        String keyVersion,
        SigningAlgorithm algorithm,
        Instant now
    ) {
        return new SignedTransaction(withdrawalId, unsignedTransactionId, assetCode, networkCode, format, serializedPayload,
            signingDigest, signature, custodyProvider, keyAlias, keyVersion, algorithm, now);
    }

    public UUID id() { return id; }
    public UUID withdrawalId() { return withdrawalId; }
    public String assetCode() { return assetCode; }
    public String networkCode() { return networkCode; }
    public String format() { return format; }
    public String serializedPayload() { return serializedPayload; }
    public String signingDigest() { return signingDigest; }
    public String custodyProvider() { return custodyProvider; }
    public String keyAlias() { return keyAlias; }
    public String keyVersion() { return keyVersion; }
    public SigningAlgorithm algorithm() { return algorithm; }
    public Instant signedAt() { return signedAt; }
}
