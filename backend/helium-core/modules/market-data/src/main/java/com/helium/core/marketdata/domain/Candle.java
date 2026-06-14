package com.helium.core.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_data_candles")
public class Candle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "market_symbol", nullable = false, length = 41)
    private String marketSymbol;

    @Column(name = "interval_name", nullable = false, length = 16)
    private String intervalName;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "close_time", nullable = false)
    private Instant closeTime;

    @Column(name = "open_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false, precision = 38, scale = 18)
    private BigDecimal volume;

    @Column(name = "quote_volume", nullable = false, precision = 38, scale = 18)
    private BigDecimal quoteVolume;

    @Column(name = "trade_count", nullable = false)
    private long tradeCount;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    protected Candle() {
    }

    private Candle(String marketSymbol, String intervalName, Instant openTime, Instant closeTime, BigDecimal price, BigDecimal quantity) {
        this.marketSymbol = MarketSymbol.normalize(marketSymbol);
        this.intervalName = intervalName;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.openPrice = MarketDataNumbers.positive(price, "price");
        this.highPrice = this.openPrice;
        this.lowPrice = this.openPrice;
        this.closePrice = this.openPrice;
        this.volume = MarketDataNumbers.positive(quantity, "quantity");
        this.quoteVolume = price.multiply(quantity).stripTrailingZeros();
        this.tradeCount = 1;
        this.closed = false;
    }

    public static Candle open(String marketSymbol, String intervalName, Instant openTime, Instant closeTime, BigDecimal price, BigDecimal quantity) {
        return new Candle(marketSymbol, intervalName, openTime, closeTime, price, quantity);
    }

    public void addTrade(BigDecimal price, BigDecimal quantity) {
        BigDecimal normalizedPrice = MarketDataNumbers.positive(price, "price");
        BigDecimal normalizedQuantity = MarketDataNumbers.positive(quantity, "quantity");
        if (normalizedPrice.compareTo(highPrice) > 0) {
            highPrice = normalizedPrice;
        }
        if (normalizedPrice.compareTo(lowPrice) < 0) {
            lowPrice = normalizedPrice;
        }
        closePrice = normalizedPrice;
        volume = volume.add(normalizedQuantity).stripTrailingZeros();
        quoteVolume = quoteVolume.add(normalizedPrice.multiply(normalizedQuantity)).stripTrailingZeros();
        tradeCount++;
    }

    public void close() {
        this.closed = true;
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public Instant closeTime() {
        return closeTime;
    }

    public boolean closed() {
        return closed;
    }

    public BigDecimal openPrice() {
        return openPrice;
    }

    public BigDecimal highPrice() {
        return highPrice;
    }

    public BigDecimal lowPrice() {
        return lowPrice;
    }

    public BigDecimal closePrice() {
        return closePrice;
    }

    public BigDecimal volume() {
        return volume;
    }

    public long tradeCount() {
        return tradeCount;
    }
}
