package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TimeInForce;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class TradingHash {
    private TradingHash() {
    }

    static String orderPlacementHash(
        UUID userId,
        String market,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        TimeInForce timeInForce
    ) {
        return hash(
            part(userId.toString())
                + part(market.toUpperCase())
                + part(side.name())
                + part(type.name())
                + part(canonical(quantity))
                + part(canonical(price))
                + part(timeInForce.name())
        );
    }

    static String executionHash(
        String executionId,
        long marketSequence,
        long buyerOrderOffset,
        long sellerOrderOffset,
        UUID buyerOrderId,
        UUID sellerOrderId,
        String market,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal buyerFeeAmount,
        BigDecimal sellerFeeAmount
    ) {
        return hash(
            part(executionId)
                + part(Long.toString(marketSequence))
                + part(Long.toString(buyerOrderOffset))
                + part(Long.toString(sellerOrderOffset))
                + part(buyerOrderId.toString())
                + part(sellerOrderId.toString())
                + part(market.toUpperCase())
                + part(canonical(quantity))
                + part(canonical(price))
                + part(canonical(buyerFeeAmount))
                + part(canonical(sellerFeeAmount))
        );
    }

    private static String canonical(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String part(String value) {
        return value.length() + ":" + value + "|";
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
