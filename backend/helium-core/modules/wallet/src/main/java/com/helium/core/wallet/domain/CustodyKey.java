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
@Table(name = "wallet_custody_keys")
public class CustodyKey {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Column(name = "key_alias", nullable = false, length = 160)
    private String keyAlias;

    @Column(name = "key_version", nullable = false, length = 80)
    private String keyVersion;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, length = 40)
    private SigningAlgorithm algorithm;

    @Column(name = "public_key_hex", columnDefinition = "text")
    private String publicKeyHex;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CustodyKeyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CustodyKey() {}

    private CustodyKey(
        String assetCode,
        String keyAlias,
        String keyVersion,
        String provider,
        SigningAlgorithm algorithm,
        String publicKeyHex,
        CustodyKeyStatus status,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.assetCode = Asset.normalizeCode(assetCode);
        this.keyAlias = BlockchainNetwork.requireText(keyAlias, "keyAlias", 160);
        this.keyVersion = BlockchainNetwork.requireText(keyVersion, "keyVersion", 80);
        this.provider = BlockchainNetwork.requireText(provider, "provider", 80);
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.publicKeyHex = publicKeyHex == null || publicKeyHex.isBlank() ? null : publicKeyHex.trim();
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.activatedAt = status == CustodyKeyStatus.ACTIVE ? now : null;
    }

    public static CustodyKey register(
        String assetCode,
        String keyAlias,
        String keyVersion,
        String provider,
        SigningAlgorithm algorithm,
        String publicKeyHex,
        CustodyKeyStatus status,
        Instant now
    ) {
        return new CustodyKey(assetCode, keyAlias, keyVersion, provider, algorithm, publicKeyHex, status, now);
    }

    public void activate(Instant now) {
        if (status == CustodyKeyStatus.RETIRED) {
            throw new WalletValidationException("retired custody keys cannot be activated");
        }
        this.status = CustodyKeyStatus.ACTIVE;
        this.activatedAt = Objects.requireNonNull(now, "now");
        this.retiredAt = null;
    }

    public void verifyOnly(Instant now) {
        if (status == CustodyKeyStatus.RETIRED) {
            throw new WalletValidationException("retired custody keys cannot be moved to verify-only");
        }
        this.status = CustodyKeyStatus.VERIFY_ONLY;
        this.retiredAt = Objects.requireNonNull(now, "now");
    }

    public void retire(Instant now) {
        this.status = CustodyKeyStatus.RETIRED;
        this.retiredAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() { return id; }
    public String assetCode() { return assetCode; }
    public String keyAlias() { return keyAlias; }
    public String keyVersion() { return keyVersion; }
    public String provider() { return provider; }
    public SigningAlgorithm algorithm() { return algorithm; }
    public String publicKeyHex() { return publicKeyHex; }
    public CustodyKeyStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant activatedAt() { return activatedAt; }
    public Instant retiredAt() { return retiredAt; }
}
