package com.helium.core.trading.domain;

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
@Table(name = "trading_orders")
public class Order {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "client_order_id", nullable = false, updatable = false, length = 120)
    private String clientOrderId;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "market_symbol", nullable = false, updatable = false, length = 80)
    private String marketSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, updatable = false, length = 20)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, updatable = false, length = 20)
    private TimeInForce timeInForce;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "limit_price", updatable = false, precision = 38, scale = 18)
    private BigDecimal limitPrice;

    @Column(name = "fee_rate", nullable = false, updatable = false, precision = 18, scale = 10)
    private BigDecimal feeRate;

    @Column(name = "fee_asset_code", nullable = false, updatable = false, length = 32)
    private String feeAssetCode;

    @Column(name = "fee_policy_version", nullable = false, updatable = false, length = 120)
    private String feePolicyVersion;

    @Column(name = "filled_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal filledQuantity;

    @Column(name = "last_matching_offset", nullable = false)
    private long lastMatchingOffset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Order() {
    }

    private Order(
        UUID userId,
        String clientOrderId,
        String requestHash,
        String marketSymbol,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal feeRate,
        String feeAssetCode,
        String feePolicyVersion,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.clientOrderId = Market.requireText(clientOrderId, "clientOrderId", 120);
        this.requestHash = Market.requireText(requestHash, "requestHash", 64);
        this.marketSymbol = Market.normalizeSymbol(marketSymbol);
        this.side = Objects.requireNonNull(side, "side");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.timeInForce = Objects.requireNonNull(timeInForce, "timeInForce");
        this.quantity = Market.requirePositive(quantity, "quantity");
        this.limitPrice = limitPrice == null ? null : Market.requirePositive(limitPrice, "limitPrice");
        this.feeRate = requireRate(feeRate);
        this.feeAssetCode = Market.normalizeAsset(feeAssetCode);
        this.feePolicyVersion = Market.requireText(feePolicyVersion, "feePolicyVersion", 120);
        this.filledQuantity = BigDecimal.ZERO;
        this.lastMatchingOffset = 0;
        this.status = OrderStatus.RECEIVED;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static Order receive(
        UUID userId,
        String clientOrderId,
        String requestHash,
        String marketSymbol,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal feeRate,
        String feeAssetCode,
        String feePolicyVersion,
        Instant now
    ) {
        return new Order(
            userId,
            clientOrderId,
            requestHash,
            marketSymbol,
            side,
            orderType,
            timeInForce,
            quantity,
            limitPrice,
            feeRate,
            feeAssetCode,
            feePolicyVersion,
            now
        );
    }

    public void validateAgainst(Market market, Instant now) {
        requireStatus(OrderStatus.RECEIVED);
        if (!market.enabled()) {
            throw new TradingValidationException("market is disabled");
        }
        if (!market.symbol().equals(marketSymbol)) {
            throw new TradingValidationException("order market does not match market");
        }
        if (orderType == OrderType.LIMIT && limitPrice == null) {
            throw new TradingValidationException("limit order requires price");
        }
        if (limitPrice == null) {
            throw new TradingValidationException("order requires price for reservation");
        }
        if (quantity.compareTo(market.minOrderQuantity()) < 0) {
            throw new TradingValidationException("order quantity is below market minimum");
        }
        BigDecimal notional = quantity.multiply(limitPrice).stripTrailingZeros();
        if (notional.compareTo(market.minNotional()) < 0) {
            throw new TradingValidationException("order notional is below market minimum");
        }
        this.status = OrderStatus.VALIDATED;
        touch(now);
    }

    public void markFundsReserved(Instant now) {
        requireStatus(OrderStatus.VALIDATED);
        this.status = OrderStatus.FUNDS_RESERVED;
        touch(now);
    }

    public void markSentToMatching(Instant now) {
        requireStatus(OrderStatus.FUNDS_RESERVED);
        this.status = OrderStatus.SENT_TO_MATCHING;
        touch(now);
    }

    public void accept(long matchingOffset, Instant now) {
        requireStatus(OrderStatus.SENT_TO_MATCHING);
        requireNextMatchingOffset(matchingOffset);
        this.status = OrderStatus.OPEN;
        this.lastMatchingOffset = matchingOffset;
        touch(now);
    }

    public void startCancellation(Instant now) {
        requireStatus(OrderStatus.OPEN);
        this.status = OrderStatus.CANCEL_REQUESTED;
        touch(now);
    }

    public void cancel(long matchingOffset, Instant now) {
        requireStatus(OrderStatus.CANCEL_REQUESTED);
        requireNextMatchingOffset(matchingOffset);
        this.status = OrderStatus.CANCELLED;
        this.lastMatchingOffset = matchingOffset;
        touch(now);
    }

    public void expire(long matchingOffset, Instant now) {
        if (status != OrderStatus.OPEN && status != OrderStatus.PARTIALLY_FILLED) {
            throw new TradingValidationException("only open orders may expire");
        }
        requireNextMatchingOffset(matchingOffset);
        this.status = OrderStatus.EXPIRED;
        this.lastMatchingOffset = matchingOffset;
        touch(now);
    }

    public void reject(String reason, long matchingOffset, Instant now) {
        requireMutable();
        Market.requireText(reason, "reason", 500);
        requireNextMatchingOffset(matchingOffset);
        this.status = OrderStatus.REJECTED;
        this.lastMatchingOffset = matchingOffset;
        touch(now);
    }

    public void applyFill(BigDecimal fillQuantity, long matchingOffset, Instant now) {
        requireMutable();
        boolean cancelRequested = status == OrderStatus.CANCEL_REQUESTED;
        if (status != OrderStatus.OPEN && status != OrderStatus.PARTIALLY_FILLED && !cancelRequested) {
            throw new TradingValidationException("order must be open to apply a fill");
        }
        requireNextMatchingOffset(matchingOffset);
        BigDecimal normalizedFill = Market.requirePositive(fillQuantity, "fillQuantity");
        BigDecimal nextFilled = filledQuantity.add(normalizedFill).stripTrailingZeros();
        if (nextFilled.compareTo(quantity) > 0) {
            throw new TradingValidationException("filled quantity cannot exceed order quantity");
        }
        this.filledQuantity = nextFilled;
        this.lastMatchingOffset = matchingOffset;
        this.status = nextFilled.compareTo(quantity) == 0
            ? OrderStatus.FILLED
            : cancelRequested ? OrderStatus.CANCEL_REQUESTED : OrderStatus.PARTIALLY_FILLED;
        touch(now);
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(filledQuantity).stripTrailingZeros();
    }

    public boolean terminal() {
        return status.terminal();
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String clientOrderId() {
        return clientOrderId;
    }

    public String requestHash() {
        return requestHash;
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public OrderSide side() {
        return side;
    }

    public OrderType orderType() {
        return orderType;
    }

    public OrderStatus status() {
        return status;
    }

    public TimeInForce timeInForce() {
        return timeInForce;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public BigDecimal limitPrice() {
        return limitPrice;
    }

    public BigDecimal feeRate() {
        return feeRate;
    }

    public String feeAssetCode() {
        return feeAssetCode;
    }

    public String feePolicyVersion() {
        return feePolicyVersion;
    }

    public BigDecimal filledQuantity() {
        return filledQuantity;
    }

    public long lastMatchingOffset() {
        return lastMatchingOffset;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void requireStatus(OrderStatus expected) {
        requireMutable();
        if (status != expected) {
            throw new TradingValidationException("order must be " + expected + " but was " + status);
        }
    }

    private void requireMutable() {
        if (status != null && status.terminal()) {
            throw new TradingValidationException("terminal orders cannot mutate");
        }
    }

    private void requireNextMatchingOffset(long matchingOffset) {
        if (matchingOffset != lastMatchingOffset + 1) {
            throw new TradingValidationException("matching event offset is out of order");
        }
    }

    private static BigDecimal requireRate(BigDecimal value) {
        Objects.requireNonNull(value, "feeRate");
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new TradingValidationException("feeRate must be between 0 and 1");
        }
        return value.stripTrailingZeros();
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
