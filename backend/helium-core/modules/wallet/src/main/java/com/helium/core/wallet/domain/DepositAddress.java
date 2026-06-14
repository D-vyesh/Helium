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
@Table(name = "wallet_deposit_addresses")
public class DepositAddress {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "address", nullable = false, updatable = false, length = 160)
    private String address;

    @Column(name = "memo", updatable = false, length = 120)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DepositAddressStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DepositAddress() {
    }

    private DepositAddress(UUID userId, String assetCode, String networkCode, String address, String memo, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.address = BlockchainNetwork.requireText(address, "address", 160);
        this.memo = memo == null || memo.isBlank() ? null : BlockchainNetwork.requireText(memo, "memo", 120);
        this.status = DepositAddressStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static DepositAddress assign(UUID userId, String assetCode, String networkCode, String address, String memo, Instant now) {
        return new DepositAddress(userId, assetCode, networkCode, address, memo, now);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String assetCode() {
        return assetCode;
    }

    public String networkCode() {
        return networkCode;
    }

    public String address() {
        return address;
    }

    public String memo() {
        return memo;
    }
}

