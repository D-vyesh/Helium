package com.helium.core.trading.application;

import java.math.BigDecimal;

public interface MarketAdministrationPort {
    void registerMarket(RegisterMarketCommand command);
    void updateMarket(UpdateMarketCommand command);

    record RegisterMarketCommand(
        String symbol,
        String baseAsset,
        String quoteAsset,
        int priceScale,
        int quantityScale,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        boolean enabled
    ) {}

    record UpdateMarketCommand(
        String symbol,
        boolean enabled
    ) {}
}
