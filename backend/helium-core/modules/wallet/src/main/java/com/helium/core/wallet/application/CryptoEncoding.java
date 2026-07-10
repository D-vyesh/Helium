package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletValidationException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

final class CryptoEncoding {
    private static final HexFormat HEX = HexFormat.of();

    private CryptoEncoding() {}

    static String sha256Hex(byte[] payload) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static byte[] decodeHex(String value, String field) {
        String text = cleanHex(value, field);
        try {
            return HEX.parseHex(text);
        } catch (IllegalArgumentException exception) {
            throw new WalletValidationException(field + " is not valid hex");
        }
    }

    static String hex(byte[] value) {
        return HEX.formatHex(value);
    }

    static String prefixedHex(byte[] value) {
        return "0x" + hex(value);
    }

    static String cleanHex(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new WalletValidationException(field + " is required");
        }
        String text = value.trim();
        if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        if ((text.length() & 1) == 1) {
            text = "0" + text;
        }
        return text;
    }

    static byte[] decodeSignature(String signature) {
        String text = signature == null ? "" : signature.trim();
        if (text.startsWith("vault:")) {
            text = text.substring(text.lastIndexOf(':') + 1);
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return decodeHex(text, "signature");
        }
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException ignored) {
            return decodeHex(text, "signature");
        }
    }
}
