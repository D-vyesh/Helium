package com.helium.core.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "trading_markets")
public class Market {
    @Id
    @Column(name = "symbol", nullable = false, updatable = false, length = 80)
    private String symbol;

    @Column(name = "base_asset", nullable = false, updatable = false, length = 32)
    private String baseAsset;

    @Column(name = "quote_asset", nullable = false, updatable = false, length = 32)
    private String quoteAsset;

    @Column(name = "price_scale", nullable = false)
    private int priceScale;

    @Column(name = "quantity_scale", nullable = false)
    private int quantityScale;

    @Column(name = "min_order_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal minOrderQuantity;

    @Column(name = "min_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal minNotional;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Market() {
    }

    private Market(
        String symbol,
        String baseAsset,
        String quoteAsset,
        int priceScale,
        int quantityScale,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        boolean enabled,
        Instant now
    ) {
        this.symbol = normalizeSymbol(symbol);
        this.baseAsset = normalizeAsset(baseAsset);
        this.quoteAsset = normalizeAsset(quoteAsset);
        if (this.baseAsset.equals(this.quoteAsset)) {
            throw new TradingValidationException("base and quote assets must differ");
        }
        this.priceScale = requireScale(priceScale, "priceScale");
        this.quantityScale = requireScale(quantityScale, "quantityScale");
        this.minOrderQuantity = requireNonNegative(minOrderQuantity, "minOrderQuantity");
        this.minNotional = requireNonNegative(minNotional, "minNotional");
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static Market register(
        String symbol,
        String baseAsset,
        String quoteAsset,
        int priceScale,
        int quantityScale,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        boolean enabled,
        Instant now
    ) {
        return new Market(symbol, baseAsset, quoteAsset, priceScale, quantityScale, minOrderQuantity, minNotional, enabled, now);
    }

    public void updatePolicy(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public String symbol() {
        return symbol;
    }

    public String baseAsset() {
        return baseAsset;
    }

    public String quoteAsset() {
        return quoteAsset;
    }

    public BigDecimal minOrderQuantity() {
        return minOrderQuantity;
    }

    public BigDecimal minNotional() {
        return minNotional;
    }

    public boolean enabled() {
        return enabled;
    }

    public static String normalizeSymbol(String value) {
        String symbol = requireText(value, "marketSymbol", 80).toUpperCase(Locale.ROOT);
        if (!symbol.matches("[A-Z0-9]{2,32}-[A-Z0-9]{2,32}")) {
            throw new TradingValidationException("market symbol is invalid");
        }
        return symbol;
    }

    public static String normalizeAsset(String value) {
        String asset = requireText(value, "assetCode", 32).toUpperCase(Locale.ROOT);
        if (!asset.matches("[A-Z0-9]{2,32}")) {
            throw new TradingValidationException("asset code is invalid");
        }
        return asset;
    }

    public static String requireText(String value, String field, int maximumLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) {
            throw new TradingValidationException(field + " is required");
        }
        if (text.length() > maximumLength) {
            throw new TradingValidationException(field + " is too long");
        }
        return text;
    }

    public static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() <= 0) {
            throw new TradingValidationException(field + " must be positive");
        }
        return value.stripTrailingZeros();
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new TradingValidationException(field + " cannot be negative");
        }
        return value.stripTrailingZeros();
    }

    private static int requireScale(int scale, String field) {
        if (scale < 0 || scale > 18) {
            throw new TradingValidationException(field + " must be between 0 and 18");
        }
        return scale;
    }
}
