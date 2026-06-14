package com.helium.core.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_data_tickers")
public class Ticker {
    @Id
    @Column(name = "market_symbol", nullable = false, length = 41)
    private String marketSymbol;

    @Column(name = "last_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal lastPrice;

    @Column(name = "open_price_24h", nullable = false, precision = 38, scale = 18)
    private BigDecimal openPrice24h;

    @Column(name = "high_price_24h", nullable = false, precision = 38, scale = 18)
    private BigDecimal highPrice24h;

    @Column(name = "low_price_24h", nullable = false, precision = 38, scale = 18)
    private BigDecimal lowPrice24h;

    @Column(name = "volume_24h", nullable = false, precision = 38, scale = 18)
    private BigDecimal volume24h;

    @Column(name = "quote_volume_24h", nullable = false, precision = 38, scale = 18)
    private BigDecimal quoteVolume24h;

    @Column(name = "trade_count_24h", nullable = false)
    private long tradeCount24h;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Ticker() {
    }

    private Ticker(String marketSymbol, Instant now) {
        this.marketSymbol = MarketSymbol.normalize(marketSymbol);
        this.lastPrice = BigDecimal.ZERO;
        this.openPrice24h = BigDecimal.ZERO;
        this.highPrice24h = BigDecimal.ZERO;
        this.lowPrice24h = BigDecimal.ZERO;
        this.volume24h = BigDecimal.ZERO;
        this.quoteVolume24h = BigDecimal.ZERO;
        this.tradeCount24h = 0;
        this.enabled = true;
        this.updatedAt = now;
    }

    public static Ticker create(String marketSymbol, Instant now) {
        return new Ticker(marketSymbol, now);
    }

    public void updateWindow(BigDecimal lastPrice, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal volume, BigDecimal quoteVolume, long tradeCount, Instant now) {
        this.lastPrice = MarketDataNumbers.nonNegative(lastPrice, "lastPrice");
        this.openPrice24h = MarketDataNumbers.nonNegative(open, "openPrice24h");
        this.highPrice24h = MarketDataNumbers.nonNegative(high, "highPrice24h");
        this.lowPrice24h = MarketDataNumbers.nonNegative(low, "lowPrice24h");
        this.volume24h = MarketDataNumbers.nonNegative(volume, "volume24h");
        this.quoteVolume24h = MarketDataNumbers.nonNegative(quoteVolume, "quoteVolume24h");
        this.tradeCount24h = tradeCount;
        this.updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public BigDecimal lastPrice() {
        return lastPrice;
    }

    public BigDecimal volume24h() {
        return volume24h;
    }

    public boolean enabled() {
        return enabled;
    }
}
