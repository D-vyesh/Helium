package com.helium.core.marketdata.application;

import org.springframework.stereotype.Service;

@Service
public class MarketProjector {
    private final TickerProjector tickerProjector;

    public MarketProjector(TickerProjector tickerProjector) {
        this.tickerProjector = tickerProjector;
    }

    public void projectCreated(MarketDataEventPort.MarketCreated event) {
        tickerProjector.setMarketEnabled(event.marketSymbol(), event.enabled());
    }

    public void projectEnabled(MarketDataEventPort.MarketEnabled event) {
        tickerProjector.setMarketEnabled(event.marketSymbol(), true);
    }

    public void projectDisabled(MarketDataEventPort.MarketDisabled event) {
        tickerProjector.setMarketEnabled(event.marketSymbol(), false);
    }
}
