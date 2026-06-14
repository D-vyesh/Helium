package com.helium.core.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_data_public_trades")
public class PublicTrade {
    @Id
    @Column(name = "execution_id", nullable = false, length = 120)
    private String executionId;

    @Column(name = "market_symbol", nullable = false, length = 41)
    private String marketSymbol;

    @Column(name = "match_id", nullable = false, length = 120)
    private String matchId;

    @Column(name = "buyer_order_id", nullable = false)
    private UUID buyerOrderId;

    @Column(name = "seller_order_id", nullable = false)
    private UUID sellerOrderId;

    @Column(name = "maker_order_id", nullable = false)
    private UUID makerOrderId;

    @Column(name = "taker_order_id", nullable = false)
    private UUID takerOrderId;

    @Column(name = "price", nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "market_sequence", nullable = false)
    private long marketSequence;

    @Column(name = "trade_hash", nullable = false, length = 64)
    private String tradeHash;

    @Column(name = "traded_at", nullable = false)
    private Instant tradedAt;

    protected PublicTrade() {
    }

    private PublicTrade(
        String executionId,
        String matchId,
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal price,
        BigDecimal quantity,
        long marketSequence,
        String tradeHash,
        Instant tradedAt
    ) {
        this.executionId = required(executionId, "executionId");
        this.matchId = required(matchId, "matchId");
        this.marketSymbol = MarketSymbol.normalize(marketSymbol);
        this.buyerOrderId = required(buyerOrderId, "buyerOrderId");
        this.sellerOrderId = required(sellerOrderId, "sellerOrderId");
        this.makerOrderId = required(makerOrderId, "makerOrderId");
        this.takerOrderId = required(takerOrderId, "takerOrderId");
        this.price = MarketDataNumbers.positive(price, "price");
        this.quantity = MarketDataNumbers.positive(quantity, "quantity");
        if (marketSequence <= 0) {
            throw new MarketDataValidationException("market sequence must be positive");
        }
        this.marketSequence = marketSequence;
        this.tradeHash = required(tradeHash, "tradeHash");
        this.tradedAt = required(tradedAt, "tradedAt");
    }

    public static PublicTrade record(
        String executionId,
        String matchId,
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal price,
        BigDecimal quantity,
        long marketSequence,
        String tradeHash,
        Instant tradedAt
    ) {
        return new PublicTrade(executionId, matchId, marketSymbol, buyerOrderId, sellerOrderId, makerOrderId, takerOrderId, price, quantity, marketSequence, tradeHash, tradedAt);
    }

    public boolean samePayload(String hash) {
        return tradeHash.equals(hash);
    }

    public String executionId() {
        return executionId;
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public BigDecimal price() {
        return price;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public long marketSequence() {
        return marketSequence;
    }

    public Instant tradedAt() {
        return tradedAt;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new MarketDataValidationException(field + " is required");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MarketDataValidationException(field + " is required");
        }
        return value;
    }
}
