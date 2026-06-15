package com.helium.core.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_daily_balance_snapshots")
public class DailyBalanceSnapshot {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "ledger_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal ledgerTotal;

    @Column(name = "wallet_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal walletTotal;

    @Column(name = "trading_locked_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal tradingLockedTotal;

    @Column(name = "created_by", nullable = false, updatable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DailyBalanceSnapshot() {
    }

    private DailyBalanceSnapshot(
        LocalDate businessDate,
        String assetCode,
        BigDecimal ledgerTotal,
        BigDecimal walletTotal,
        BigDecimal tradingLockedTotal,
        String createdBy,
        Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate");
        this.assetCode = requireAsset(assetCode);
        this.ledgerTotal = Objects.requireNonNull(ledgerTotal, "ledgerTotal").stripTrailingZeros();
        this.walletTotal = Objects.requireNonNull(walletTotal, "walletTotal").stripTrailingZeros();
        this.tradingLockedTotal = Objects.requireNonNull(tradingLockedTotal, "tradingLockedTotal").stripTrailingZeros();
        this.createdBy = requireText(createdBy, "createdBy", 120);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DailyBalanceSnapshot capture(
        LocalDate businessDate,
        String assetCode,
        BigDecimal ledgerTotal,
        BigDecimal walletTotal,
        BigDecimal tradingLockedTotal,
        String createdBy,
        Instant createdAt
    ) {
        return new DailyBalanceSnapshot(businessDate, assetCode, ledgerTotal, walletTotal, tradingLockedTotal, createdBy, createdAt);
    }

    private static String requireAsset(String value) {
        String normalized = requireText(value, "assetCode", 32).toUpperCase();
        if (!normalized.equals(value.trim().toUpperCase())) {
            throw new AdminValidationException("assetCode is invalid");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new AdminValidationException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new AdminValidationException(field + " is too long");
        }
        return normalized;
    }
}
