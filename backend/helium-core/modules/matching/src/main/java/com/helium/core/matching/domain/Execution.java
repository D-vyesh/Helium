package com.helium.core.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "matching_executions")
public class Execution {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "execution_id", nullable = false, updatable = false, length = 160)
    private String executionId;

    @Column(name = "match_id", nullable = false, updatable = false, length = 160)
    private String matchId;

    @Column(name = "market_symbol", nullable = false, updatable = false, length = 80)
    private String marketSymbol;

    @Column(name = "buyer_order_id", nullable = false, updatable = false)
    private UUID buyerOrderId;

    @Column(name = "seller_order_id", nullable = false, updatable = false)
    private UUID sellerOrderId;

    @Column(name = "maker_order_id", nullable = false, updatable = false)
    private UUID makerOrderId;

    @Column(name = "taker_order_id", nullable = false, updatable = false)
    private UUID takerOrderId;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(name = "buyer_order_offset", nullable = false, updatable = false)
    private long buyerOrderOffset;

    @Column(name = "seller_order_offset", nullable = false, updatable = false)
    private long sellerOrderOffset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Execution() {
    }

    private Execution(
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal quantity,
        BigDecimal price,
        long sequenceNumber,
        long buyerOrderOffset,
        long sellerOrderOffset,
        Instant now
    ) {
        this.marketSymbol = MatchingText.market(marketSymbol);
        this.buyerOrderId = Objects.requireNonNull(buyerOrderId, "buyerOrderId");
        this.sellerOrderId = Objects.requireNonNull(sellerOrderId, "sellerOrderId");
        this.makerOrderId = Objects.requireNonNull(makerOrderId, "makerOrderId");
        this.takerOrderId = Objects.requireNonNull(takerOrderId, "takerOrderId");
        if (buyerOrderId.equals(sellerOrderId)) {
            throw new MatchingValidationException("buyer and seller orders must differ");
        }
        this.quantity = MatchingNumbers.positive(quantity, "quantity");
        this.price = MatchingNumbers.positive(price, "price");
        if (sequenceNumber < 1) {
            throw new MatchingValidationException("sequence number must be positive");
        }
        if (buyerOrderOffset < 2 || sellerOrderOffset < 2) {
            throw new MatchingValidationException("execution order offsets must follow acceptance");
        }
        this.sequenceNumber = sequenceNumber;
        this.buyerOrderOffset = buyerOrderOffset;
        this.sellerOrderOffset = sellerOrderOffset;
        this.executionId = "execution:" + this.marketSymbol + ":" + sequenceNumber;
        this.matchId = "match:" + this.marketSymbol + ":" + sequenceNumber;
        this.id = UUID.nameUUIDFromBytes(executionId.getBytes(StandardCharsets.UTF_8));
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static Execution create(
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal quantity,
        BigDecimal price,
        long sequenceNumber,
        long buyerOrderOffset,
        long sellerOrderOffset,
        Instant now
    ) {
        return new Execution(
            marketSymbol,
            buyerOrderId,
            sellerOrderId,
            makerOrderId,
            takerOrderId,
            quantity,
            price,
            sequenceNumber,
            buyerOrderOffset,
            sellerOrderOffset,
            now
        );
    }

    public String executionId() {
        return executionId;
    }

    public String matchId() {
        return matchId;
    }

    public UUID buyerOrderId() {
        return buyerOrderId;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public UUID makerOrderId() {
        return makerOrderId;
    }

    public UUID takerOrderId() {
        return takerOrderId;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public BigDecimal price() {
        return price;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public long buyerOrderOffset() {
        return buyerOrderOffset;
    }

    public long sellerOrderOffset() {
        return sellerOrderOffset;
    }

    public String marketSymbol() {
        return marketSymbol;
    }
}
