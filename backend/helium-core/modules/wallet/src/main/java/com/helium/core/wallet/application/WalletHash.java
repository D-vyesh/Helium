package com.helium.core.wallet.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

final class WalletHash {
    private WalletHash() {
    }

    static String withdrawalRequestHash(
        UUID userId,
        String assetCode,
        String networkCode,
        String destinationAddress,
        String destinationMemo,
        BigDecimal amount,
        BigDecimal fee
    ) {
        return sha256Hex(
            part(userId.toString())
                + part(assetCode.toUpperCase(Locale.ROOT))
                + part(networkCode.toUpperCase(Locale.ROOT))
                + part(destinationAddress)
                + part(destinationMemo == null ? "" : destinationMemo)
                + part(canonicalAmount(amount))
                + part(canonicalAmount(fee))
        );
    }

    private static String canonicalAmount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String part(String value) {
        return value.length() + ":" + value + "|";
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

