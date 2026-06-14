package com.helium.core.trading.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MarketQueryPort {
    Optional<MarketView> getMarket(String symbol);
    List<MarketView> getMarkets();

    record MarketView(
        String symbol,
        String baseAsset,
        String quoteAsset,
        int priceScale,
        int quantityScale,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        boolean enabled
    ) {}
}
