package com.helium.core.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "matching_orders")
public class BookOrder {
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "market_symbol", nullable = false, updatable = false, length = 80)
    private String marketSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, updatable = false, length = 20)
    private MatchingOrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 20)
    private MatchingOrderType orderType;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "remaining_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal remainingQuantity;

    @Column(name = "limit_price", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal limitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private MatchingOrderStatus status;

    @Column(name = "received_sequence", nullable = false, updatable = false)
    private long receivedSequence;

    @Column(name = "last_order_offset", nullable = false)
    private long lastOrderOffset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BookOrder() {
    }

    private BookOrder(
        UUID orderId,
        String requestHash,
        String marketSymbol,
        MatchingOrderSide side,
        MatchingOrderType orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        long receivedSequence,
        Instant now
    ) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.requestHash = MatchingText.require(requestHash, "requestHash", 64);
        this.marketSymbol = MatchingText.market(marketSymbol);
        this.side = Objects.requireNonNull(side, "side");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.quantity = MatchingNumbers.positive(quantity, "quantity");
        this.remainingQuantity = this.quantity;
        this.limitPrice = MatchingNumbers.positive(limitPrice, "limitPrice");
        if (receivedSequence < 1) {
            throw new MatchingValidationException("received sequence must be positive");
        }
        this.receivedSequence = receivedSequence;
        this.lastOrderOffset = 1;
        this.status = MatchingOrderStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static BookOrder accept(
        UUID orderId,
        String requestHash,
        String marketSymbol,
        MatchingOrderSide side,
        MatchingOrderType orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        long receivedSequence,
        Instant now
    ) {
        return new BookOrder(orderId, requestHash, marketSymbol, side, orderType, quantity, limitPrice, receivedSequence, now);
    }

    public boolean crosses(BookOrder restingOrder) {
        if (!matchable() || !restingOrder.matchable() || side == restingOrder.side) {
            return false;
        }
        if (side == MatchingOrderSide.BUY) {
            return limitPrice.compareTo(restingOrder.limitPrice) >= 0;
        }
        return limitPrice.compareTo(restingOrder.limitPrice) <= 0;
    }

    public long fill(BigDecimal fillQuantity, Instant now) {
        BigDecimal normalizedFill = MatchingNumbers.positive(fillQuantity, "fillQuantity");
        BigDecimal nextRemaining = remainingQuantity.subtract(normalizedFill).stripTrailingZeros();
        if (nextRemaining.signum() < 0) {
            throw new MatchingValidationException("fill quantity exceeds remaining quantity");
        }
        this.lastOrderOffset++;
        this.remainingQuantity = nextRemaining;
        this.status = nextRemaining.signum() == 0 ? MatchingOrderStatus.FILLED : MatchingOrderStatus.PARTIALLY_FILLED;
        this.updatedAt = Objects.requireNonNull(now, "now");
        return lastOrderOffset;
    }

    public long cancel(Instant now) {
        if (!matchable()) {
            throw new MatchingValidationException("order is not cancellable");
        }
        this.lastOrderOffset++;
        this.status = MatchingOrderStatus.CANCELLED;
        this.updatedAt = Objects.requireNonNull(now, "now");
        return lastOrderOffset;
    }

    public long expire(Instant now) {
        if (!matchable()) {
            throw new MatchingValidationException("order is not expirable");
        }
        this.lastOrderOffset++;
        this.status = MatchingOrderStatus.EXPIRED;
        this.updatedAt = Objects.requireNonNull(now, "now");
        return lastOrderOffset;
    }

    public boolean samePayload(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public boolean matchable() {
        return status.matchable() && remainingQuantity.signum() > 0;
    }

    public UUID orderId() {
        return orderId;
    }

    public String requestHash() {
        return requestHash;
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public MatchingOrderSide side() {
        return side;
    }

    public BigDecimal remainingQuantity() {
        return remainingQuantity;
    }

    public BigDecimal limitPrice() {
        return limitPrice;
    }

    public MatchingOrderStatus status() {
        return status;
    }

    public long receivedSequence() {
        return receivedSequence;
    }

    public long lastOrderOffset() {
        return lastOrderOffset;
    }
}
