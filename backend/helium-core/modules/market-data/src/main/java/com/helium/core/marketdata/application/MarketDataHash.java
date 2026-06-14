package com.helium.core.marketdata.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

final class MarketDataHash {
    private MarketDataHash() {
    }

    static String execution(
        String executionId,
        String matchId,
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal quantity,
        BigDecimal price,
        long sequence
    ) {
        return sha256(part(executionId) + part(matchId) + part(marketSymbol) + part(buyerOrderId.toString())
            + part(sellerOrderId.toString()) + part(makerOrderId.toString()) + part(takerOrderId.toString())
            + part(number(quantity)) + part(number(price)) + part(Long.toString(sequence)));
    }

    static String snapshot(String marketSymbol, long sequence, String bidsJson, String asksJson) {
        return sha256(part(marketSymbol) + part(Long.toString(sequence)) + part(bidsJson) + part(asksJson));
    }

    static String delta(String marketSymbol, long sequence, String side, BigDecimal price, BigDecimal quantity, String action) {
        return sha256(part(marketSymbol) + part(Long.toString(sequence)) + part(side) + part(number(price)) + part(number(quantity)) + part(action));
    }

    private static String part(String value) {
        return value.length() + ":" + value + "|";
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
