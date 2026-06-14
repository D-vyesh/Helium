package com.helium.core.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "trading_fee_schedules")
public class FeeSchedule {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "market_symbol", nullable = false, length = 80)
    private String marketSymbol;

    @Column(name = "maker_fee_rate", nullable = false, precision = 18, scale = 10)
    private BigDecimal makerFeeRate;

    @Column(name = "taker_fee_rate", nullable = false, precision = 18, scale = 10)
    private BigDecimal takerFeeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sell_fee_asset", nullable = false, length = 20)
    private FeeAssetType sellFeeAsset;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeeSchedule() {
    }

    private FeeSchedule(
        String marketSymbol,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        FeeAssetType sellFeeAsset,
        boolean enabled,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.marketSymbol = Market.normalizeSymbol(marketSymbol);
        this.makerFeeRate = requireRate(makerFeeRate, "makerFeeRate");
        this.takerFeeRate = requireRate(takerFeeRate, "takerFeeRate");
        this.sellFeeAsset = Objects.requireNonNull(sellFeeAsset, "sellFeeAsset");
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static FeeSchedule configure(
        String marketSymbol,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        FeeAssetType sellFeeAsset,
        boolean enabled,
        Instant now
    ) {
        return new FeeSchedule(marketSymbol, makerFeeRate, takerFeeRate, sellFeeAsset, enabled, now);
    }

    public void update(BigDecimal makerFeeRate, BigDecimal takerFeeRate, FeeAssetType sellFeeAsset, boolean enabled, Instant now) {
        this.makerFeeRate = requireRate(makerFeeRate, "makerFeeRate");
        this.takerFeeRate = requireRate(takerFeeRate, "takerFeeRate");
        this.sellFeeAsset = Objects.requireNonNull(sellFeeAsset, "sellFeeAsset");
        this.enabled = enabled;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public UUID id() {
        return id;
    }

    public BigDecimal makerFeeRate() {
        return makerFeeRate;
    }

    public BigDecimal takerFeeRate() {
        return takerFeeRate;
    }

    public FeeAssetType sellFeeAsset() {
        return sellFeeAsset;
    }

    public boolean enabled() {
        return enabled;
    }

    private static BigDecimal requireRate(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new TradingValidationException(field + " must be between 0 and 1");
        }
        return value.stripTrailingZeros();
    }
}
