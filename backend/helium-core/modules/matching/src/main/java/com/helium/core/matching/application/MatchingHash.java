package com.helium.core.matching.application;

import com.helium.core.matching.domain.MatchingOrderSide;
import com.helium.core.matching.domain.MatchingOrderType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class MatchingHash {
    private MatchingHash() {
    }

    static String submissionHash(
        UUID orderId,
        String marketSymbol,
        MatchingOrderSide side,
        MatchingOrderType orderType,
        BigDecimal quantity,
        BigDecimal limitPrice
    ) {
        return hash(
            part(orderId.toString())
                + part(marketSymbol.toUpperCase())
                + part(side.name())
                + part(orderType.name())
                + part(canonical(quantity))
                + part(canonical(limitPrice))
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
