package com.helium.core.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "ledger_accounts",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ledger_accounts_owner_asset_balance",
        columnNames = {"owner_type", "owner_id", "asset_code", "balance_type"}
    )
)
public class LedgerAccount {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 40)
    private LedgerAccountOwnerType ownerType;

    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_type", nullable = false, length = 40)
    private BalanceType balanceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private LedgerAccountStatus status;

    @Column(name = "negative_balance_allowed", nullable = false)
    private boolean negativeBalanceAllowed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccount() {
    }

    private LedgerAccount(
        LedgerAccountOwnerType ownerType,
        String ownerId,
        String assetCode,
        BalanceType balanceType,
        boolean negativeBalanceAllowed
    ) {
        this.id = UUID.randomUUID();
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.ownerId = requireText(ownerId, "ownerId");
        this.assetCode = normalizeAssetCode(assetCode);
        this.balanceType = Objects.requireNonNull(balanceType, "balanceType");
        this.status = LedgerAccountStatus.ACTIVE;
        this.negativeBalanceAllowed = negativeBalanceAllowed;
        this.createdAt = Instant.now();
    }

    public static LedgerAccount open(LedgerAccountOwnerType ownerType, String ownerId, String assetCode, BalanceType balanceType) {
        return new LedgerAccount(ownerType, ownerId, assetCode, balanceType, false);
    }

    public static LedgerAccount open(
        LedgerAccountOwnerType ownerType,
        String ownerId,
        String assetCode,
        BalanceType balanceType,
        boolean negativeBalanceAllowed
    ) {
        return new LedgerAccount(ownerType, ownerId, assetCode, balanceType, negativeBalanceAllowed);
    }

    public UUID id() {
        return id;
    }

    public LedgerAccountOwnerType ownerType() {
        return ownerType;
    }

    public String ownerId() {
        return ownerId;
    }

    public String assetCode() {
        return assetCode;
    }

    public BalanceType balanceType() {
        return balanceType;
    }

    public LedgerAccountStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == LedgerAccountStatus.ACTIVE;
    }

    public PostingDirection normalBalanceDirection() {
        return ownerType.normalBalanceDirection();
    }

    public boolean negativeBalanceAllowed() {
        return negativeBalanceAllowed;
    }

    private static String normalizeAssetCode(String value) {
        String assetCode = requireText(value, "assetCode").toUpperCase();
        if (assetCode.length() > 32) {
            throw new LedgerValidationException("assetCode is too long");
        }
        return assetCode;
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new LedgerValidationException(field + " is required");
        }
        return value;
    }
}
