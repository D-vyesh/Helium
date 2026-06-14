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
@Table(name = "market_data_order_book_deltas")
public class OrderBookDelta {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "market_symbol", nullable = false, length = 41)
    private String marketSymbol;

    @Column(name = "market_sequence", nullable = false)
    private long marketSequence;

    @Column(name = "side", nullable = false, length = 4)
    private String side;

    @Column(name = "price", nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "delta_hash", nullable = false, length = 64)
    private String deltaHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderBookDelta() {
    }

    private OrderBookDelta(String marketSymbol, long marketSequence, String side, BigDecimal price, BigDecimal quantity, String action, String deltaHash, Instant createdAt) {
        this.marketSymbol = MarketSymbol.normalize(marketSymbol);
        if (marketSequence <= 0) {
            throw new MarketDataValidationException("market sequence must be positive");
        }
        this.marketSequence = marketSequence;
        this.side = side;
        this.price = MarketDataNumbers.positive(price, "price");
        this.quantity = MarketDataNumbers.nonNegative(quantity, "quantity");
        this.action = action;
        this.deltaHash = deltaHash;
        this.createdAt = createdAt;
    }

    public static OrderBookDelta create(String marketSymbol, long marketSequence, String side, BigDecimal price, BigDecimal quantity, String action, String deltaHash, Instant createdAt) {
        return new OrderBookDelta(marketSymbol, marketSequence, side, price, quantity, action, deltaHash, createdAt);
    }

    public boolean samePayload(String hash) {
        return deltaHash.equals(hash);
    }
}
