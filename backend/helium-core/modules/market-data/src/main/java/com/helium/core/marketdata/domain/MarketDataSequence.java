package com.helium.core.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "market_data_sequences")
public class MarketDataSequence {
    @Id
    @Column(name = "market_symbol", nullable = false, length = 41)
    private String marketSymbol;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarketDataSequence() {
    }

    private MarketDataSequence(String marketSymbol, Instant now) {
        this.marketSymbol = MarketSymbol.normalize(marketSymbol);
        this.lastSequence = 0;
        this.updatedAt = now;
    }

    public static MarketDataSequence start(String marketSymbol, Instant now) {
        return new MarketDataSequence(marketSymbol, now);
    }

    public void apply(long sequence, Instant now) {
        if (sequence <= 0) {
            throw new MarketDataValidationException("market data sequence must be positive");
        }
        long expected = lastSequence + 1;
        if (sequence != expected) {
            throw new MarketDataValidationException("market data sequence gap detected");
        }
        this.lastSequence = sequence;
        this.updatedAt = now;
    }

    public long lastSequence() {
        return lastSequence;
    }
}
