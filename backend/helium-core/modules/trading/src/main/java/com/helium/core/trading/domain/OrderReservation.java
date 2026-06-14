package com.helium.core.trading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "trading_order_reservations")
public class OrderReservation {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "reserved_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal reservedAmount;

    @Column(name = "estimated_fee", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal estimatedFee;

    @Column(name = "reserve_ledger_transaction_id", nullable = false, updatable = false)
    private UUID reserveLedgerTransactionId;

    @Column(name = "release_ledger_transaction_id")
    private UUID releaseLedgerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected OrderReservation() {
    }

    private OrderReservation(
        UUID orderId,
        String assetCode,
        BigDecimal reservedAmount,
        BigDecimal estimatedFee,
        UUID reserveLedgerTransactionId,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.assetCode = Market.normalizeAsset(assetCode);
        this.reservedAmount = Market.requirePositive(reservedAmount, "reservedAmount");
        this.estimatedFee = Market.requireNonNegative(estimatedFee, "estimatedFee");
        this.reserveLedgerTransactionId = Objects.requireNonNull(reserveLedgerTransactionId, "reserveLedgerTransactionId");
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static OrderReservation active(
        UUID orderId,
        String assetCode,
        BigDecimal reservedAmount,
        BigDecimal estimatedFee,
        UUID reserveLedgerTransactionId,
        Instant now
    ) {
        return new OrderReservation(orderId, assetCode, reservedAmount, estimatedFee, reserveLedgerTransactionId, now);
    }

    public void release(UUID ledgerTransactionId, Instant now) {
        if (status != ReservationStatus.ACTIVE) {
            throw new TradingValidationException("reservation is not active");
        }
        this.status = ReservationStatus.RELEASED;
        this.releaseLedgerTransactionId = ledgerTransactionId;
        this.releasedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public String assetCode() {
        return assetCode;
    }

    public BigDecimal reservedAmount() {
        return reservedAmount;
    }

    public BigDecimal estimatedFee() {
        return estimatedFee;
    }

    public ReservationStatus status() {
        return status;
    }
}
