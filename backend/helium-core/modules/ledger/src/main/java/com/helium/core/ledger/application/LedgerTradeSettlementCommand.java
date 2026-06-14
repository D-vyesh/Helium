package com.helium.core.ledger.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record LedgerTradeSettlementCommand(
    String executionId,
    UUID buyerOrderId,
    UUID sellerOrderId,
    UUID buyerUserId,
    UUID sellerUserId,
    String marketSymbol,
    String baseAsset,
    String quoteAsset,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal buyerFeeAmount,
    String buyerFeeAssetCode,
    BigDecimal sellerFeeAmount,
    String sellerFeeAssetCode,
    String idempotencyKey,
    String actorId
) {
    public LedgerTradeSettlementCommand {
        Objects.requireNonNull(buyerOrderId, "buyerOrderId");
        Objects.requireNonNull(sellerOrderId, "sellerOrderId");
        Objects.requireNonNull(buyerUserId, "buyerUserId");
        Objects.requireNonNull(sellerUserId, "sellerUserId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(buyerFeeAmount, "buyerFeeAmount");
        Objects.requireNonNull(sellerFeeAmount, "sellerFeeAmount");
        executionId = requireText(executionId, "executionId");
        marketSymbol = requireText(marketSymbol, "marketSymbol").toUpperCase();
        baseAsset = requireText(baseAsset, "baseAsset").toUpperCase();
        quoteAsset = requireText(quoteAsset, "quoteAsset").toUpperCase();
        buyerFeeAssetCode = requireText(buyerFeeAssetCode, "buyerFeeAssetCode").toUpperCase();
        sellerFeeAssetCode = requireText(sellerFeeAssetCode, "sellerFeeAssetCode").toUpperCase();
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        actorId = requireText(actorId, "actorId");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (buyerOrderId.equals(sellerOrderId)) {
            throw new IllegalArgumentException("buyer and seller order ids must differ");
        }
        if (buyerUserId.equals(sellerUserId)) {
            throw new IllegalArgumentException("buyer and seller user ids must differ");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (buyerFeeAmount.signum() < 0) {
            throw new IllegalArgumentException("buyerFeeAmount cannot be negative");
        }
        if (sellerFeeAmount.signum() < 0) {
            throw new IllegalArgumentException("sellerFeeAmount cannot be negative");
        }
        if (!buyerFeeAssetCode.equals(baseAsset) && !buyerFeeAssetCode.equals(quoteAsset)) {
            throw new IllegalArgumentException("buyer fee asset must be base or quote asset");
        }
        if (!sellerFeeAssetCode.equals(baseAsset) && !sellerFeeAssetCode.equals(quoteAsset)) {
            throw new IllegalArgumentException("seller fee asset must be base or quote asset");
        }
        quantity = quantity.stripTrailingZeros();
        price = price.stripTrailingZeros();
        buyerFeeAmount = buyerFeeAmount.stripTrailingZeros();
        sellerFeeAmount = sellerFeeAmount.stripTrailingZeros();
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
