package com.helium.core.marketdata.application;

public interface MarketDataPublishedEventPort {
    void publicTradeRecorded(String marketSymbol, String executionId);

    void tickerUpdated(String marketSymbol);

    void candleClosed(String marketSymbol, String intervalName);

    void orderBookUpdated(String marketSymbol, long sequence);

    void marketsUpdated();
}
