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

    /**
     * Stop-price used by STOP_LIMIT orders. Null for all other order types.
     * A BUY stop is triggered when lastTradePrice >= stopPrice.
     * A SELL stop is triggered when lastTradePrice <= stopPrice.
     */
    @Column(name = "stop_price", precision = 38, scale = 18)
    private BigDecimal stopPrice;

    @Column(name = "time_in_force", nullable = false, updatable = false, length = 20)
    private String timeInForce;

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
        BigDecimal stopPrice,
        String timeInForce,
        MatchingOrderStatus initialStatus,
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
        this.stopPrice = stopPrice == null ? null : MatchingNumbers.positive(stopPrice, "stopPrice");
        this.timeInForce = Objects.requireNonNull(timeInForce, "timeInForce");
        if (receivedSequence < 1) {
            throw new MatchingValidationException("received sequence must be positive");
        }
        this.receivedSequence = receivedSequence;
        this.lastOrderOffset = 1;
        this.status = Objects.requireNonNull(initialStatus, "initialStatus");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** Factory for LIMIT, MARKET, and POST_ONLY orders — starts ACTIVE. */
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
        return new BookOrder(
            orderId, requestHash, marketSymbol, side, orderType,
            quantity, limitPrice, null, "GTC",
            MatchingOrderStatus.ACTIVE, receivedSequence, now
        );
    }

    /** Factory for STOP_LIMIT orders — starts STOP_PENDING. Requires stopPrice. */
    public static BookOrder acceptStop(
        UUID orderId,
        String requestHash,
        String marketSymbol,
        MatchingOrderSide side,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        String timeInForce,
        long receivedSequence,
        Instant now
    ) {
        Objects.requireNonNull(stopPrice, "stopPrice is required for STOP_LIMIT orders");
        return new BookOrder(
            orderId, requestHash, marketSymbol, side, MatchingOrderType.STOP_LIMIT,
            quantity, limitPrice, stopPrice, timeInForce,
            MatchingOrderStatus.STOP_PENDING, receivedSequence, now
        );
    }

    /**
     * Releases a stop-limit order into the active book.
     * Transitions STOP_PENDING → ACTIVE and clears the stopPrice to free the partial index slot.
     */
    public void release(Instant now) {
        if (status != MatchingOrderStatus.STOP_PENDING) {
            throw new MatchingValidationException("only STOP_PENDING orders can be released");
        }
        this.status = MatchingOrderStatus.ACTIVE;
        this.stopPrice = null;
        this.lastOrderOffset++;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * Returns true when a trade at {@code lastTradePrice} should trigger this stop-limit order.
     * Convention: BUY stop fires when price rises to or above the stop; SELL stop fires when
     * price falls to or below the stop.
     */
    public boolean isStopTriggered(BigDecimal lastTradePrice) {
        if (status != MatchingOrderStatus.STOP_PENDING || stopPrice == null) {
            return false;
        }
        if (side == MatchingOrderSide.BUY) {
            return lastTradePrice.compareTo(stopPrice) >= 0;
        }
        return lastTradePrice.compareTo(stopPrice) <= 0;
    }

    public boolean crosses(BookOrder restingOrder) {
        if (!matchable() || !restingOrder.matchable() || side == restingOrder.side) {
            return false;
        }
        if (orderType == MatchingOrderType.MARKET) {
            // MARKET orders always cross any resting order
            return true;
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

    public long reject(Instant now) {
        if (status != MatchingOrderStatus.ACTIVE && status != MatchingOrderStatus.STOP_PENDING) {
            throw new MatchingValidationException("order cannot be rejected in status " + status);
        }
        this.lastOrderOffset++;
        this.status = MatchingOrderStatus.REJECTED;
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

    public MatchingOrderType orderType() {
        return orderType;
    }

    public BigDecimal remainingQuantity() {
        return remainingQuantity;
    }

    public BigDecimal limitPrice() {
        return limitPrice;
    }

    public BigDecimal stopPrice() {
        return stopPrice;
    }

    public String timeInForce() {
        return timeInForce;
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
