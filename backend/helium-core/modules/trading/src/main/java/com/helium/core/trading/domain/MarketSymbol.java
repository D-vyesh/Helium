package com.helium.core.trading.domain;

public record MarketSymbol(String value) {
    public MarketSymbol {
        value = Market.normalizeSymbol(value);
    }
}
