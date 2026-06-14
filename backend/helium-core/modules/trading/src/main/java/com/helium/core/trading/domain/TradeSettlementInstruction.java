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
@Table(name = "trading_settlement_instructions")
public class TradeSettlementInstruction {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "execution_id", nullable = false, updatable = false, length = 120)
    private String executionId;

    @Column(name = "execution_hash", nullable = false, updatable = false, length = 64)
    private String executionHash;

    @Column(name = "execution_sequence", nullable = false, updatable = false)
    private long executionSequence;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "buyer_order_id", nullable = false, updatable = false)
    private UUID buyerOrderId;

    @Column(name = "seller_order_id", nullable = false, updatable = false)
    private UUID sellerOrderId;

    @Column(name = "market_symbol", nullable = false, updatable = false, length = 80)
    private String marketSymbol;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "fee_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal feeAmount;

    @Column(name = "fee_asset_code", nullable = false, updatable = false, length = 32)
    private String feeAssetCode;

    @Column(name = "buyer_fee_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal buyerFeeAmount;

    @Column(name = "buyer_fee_asset_code", nullable = false, updatable = false, length = 32)
    private String buyerFeeAssetCode;

    @Column(name = "seller_fee_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal sellerFeeAmount;

    @Column(name = "seller_fee_asset_code", nullable = false, updatable = false, length = 32)
    private String sellerFeeAssetCode;

    @Column(name = "reserve_consumed_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal reserveConsumedAmount;

    @Column(name = "buyer_reserve_consumed_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal buyerReserveConsumedAmount;

    @Column(name = "seller_reserve_consumed_amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal sellerReserveConsumedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SettlementInstructionStatus status;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected TradeSettlementInstruction() {
    }

    private TradeSettlementInstruction(
        String executionId,
        String executionHash,
        long executionSequence,
        UUID buyerOrderId,
        UUID sellerOrderId,
        String marketSymbol,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal buyerFeeAmount,
        String buyerFeeAssetCode,
        BigDecimal sellerFeeAmount,
        String sellerFeeAssetCode,
        BigDecimal buyerReserveConsumedAmount,
        BigDecimal sellerReserveConsumedAmount,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.executionId = Market.requireText(executionId, "executionId", 120);
        this.executionHash = Market.requireText(executionHash, "executionHash", 64);
        if (executionSequence < 1) {
            throw new TradingValidationException("execution sequence must be positive");
        }
        this.executionSequence = executionSequence;
        this.buyerOrderId = Objects.requireNonNull(buyerOrderId, "buyerOrderId");
        this.sellerOrderId = Objects.requireNonNull(sellerOrderId, "sellerOrderId");
        if (this.buyerOrderId.equals(this.sellerOrderId)) {
            throw new TradingValidationException("buyer and seller orders must differ");
        }
        this.orderId = this.buyerOrderId;
        this.marketSymbol = Market.normalizeSymbol(marketSymbol);
        this.quantity = Market.requirePositive(quantity, "quantity");
        this.price = Market.requirePositive(price, "price");
        this.buyerFeeAmount = Market.requireNonNegative(buyerFeeAmount, "buyerFeeAmount");
        this.buyerFeeAssetCode = Market.normalizeAsset(buyerFeeAssetCode);
        this.sellerFeeAmount = Market.requireNonNegative(sellerFeeAmount, "sellerFeeAmount");
        this.sellerFeeAssetCode = Market.normalizeAsset(sellerFeeAssetCode);
        this.feeAmount = this.buyerFeeAmount;
        this.feeAssetCode = this.buyerFeeAssetCode;
        this.buyerReserveConsumedAmount = Market.requireNonNegative(buyerReserveConsumedAmount, "buyerReserveConsumedAmount");
        this.sellerReserveConsumedAmount = Market.requireNonNegative(sellerReserveConsumedAmount, "sellerReserveConsumedAmount");
        this.reserveConsumedAmount = this.buyerReserveConsumedAmount;
        this.status = SettlementInstructionStatus.PENDING;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static TradeSettlementInstruction pending(
        String executionId,
        String executionHash,
        long executionSequence,
        UUID buyerOrderId,
        UUID sellerOrderId,
        String marketSymbol,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal buyerFeeAmount,
        String buyerFeeAssetCode,
        BigDecimal sellerFeeAmount,
        String sellerFeeAssetCode,
        BigDecimal buyerReserveConsumedAmount,
        BigDecimal sellerReserveConsumedAmount,
        Instant now
    ) {
        return new TradeSettlementInstruction(
            executionId,
            executionHash,
            executionSequence,
            buyerOrderId,
            sellerOrderId,
            marketSymbol,
            quantity,
            price,
            buyerFeeAmount,
            buyerFeeAssetCode,
            sellerFeeAmount,
            sellerFeeAssetCode,
            buyerReserveConsumedAmount,
            sellerReserveConsumedAmount,
            now
        );
    }

    public void markSettled(UUID ledgerTransactionId, Instant now) {
        if (status != SettlementInstructionStatus.PENDING) {
            throw new TradingValidationException("settlement instruction is not pending");
        }
        this.status = SettlementInstructionStatus.SETTLED;
        this.ledgerTransactionId = ledgerTransactionId;
        this.settledAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() {
        return id;
    }

    public String executionId() {
        return executionId;
    }

    public String executionHash() {
        return executionHash;
    }

    public long executionSequence() {
        return executionSequence;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID buyerOrderId() {
        return buyerOrderId;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public BigDecimal price() {
        return price;
    }

    public BigDecimal feeAmount() {
        return feeAmount;
    }

    public String feeAssetCode() {
        return feeAssetCode;
    }

    public BigDecimal buyerFeeAmount() {
        return buyerFeeAmount;
    }

    public String buyerFeeAssetCode() {
        return buyerFeeAssetCode;
    }

    public BigDecimal sellerFeeAmount() {
        return sellerFeeAmount;
    }

    public String sellerFeeAssetCode() {
        return sellerFeeAssetCode;
    }

    public BigDecimal reserveConsumedAmount() {
        return reserveConsumedAmount;
    }

    public BigDecimal buyerReserveConsumedAmount() {
        return buyerReserveConsumedAmount;
    }

    public BigDecimal sellerReserveConsumedAmount() {
        return sellerReserveConsumedAmount;
    }

    public SettlementInstructionStatus status() {
        return status;
    }
}
