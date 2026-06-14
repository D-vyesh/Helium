package com.helium.core.marketdata.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MarketSymbol {
    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9]{2,20}-[A-Z0-9]{2,20}$");

    private MarketSymbol() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new MarketDataValidationException("market symbol is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new MarketDataValidationException("market symbol is invalid");
        }
        return normalized;
    }
}
