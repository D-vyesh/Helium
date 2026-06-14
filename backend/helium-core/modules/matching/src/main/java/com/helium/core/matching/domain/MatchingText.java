package com.helium.core.matching.domain;

import java.util.Locale;
import java.util.Objects;

public final class MatchingText {
    private MatchingText() {
    }

    public static String require(String value, String field, int maxLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) {
            throw new MatchingValidationException(field + " is required");
        }
        if (text.length() > maxLength) {
            throw new MatchingValidationException(field + " is too long");
        }
        return text;
    }

    public static String market(String value) {
        String symbol = require(value, "marketSymbol", 80).toUpperCase(Locale.ROOT);
        if (!symbol.matches("[A-Z0-9]{2,32}-[A-Z0-9]{2,32}")) {
            throw new MatchingValidationException("market symbol is invalid");
        }
        return symbol;
    }
}
