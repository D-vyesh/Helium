package com.helium.core.marketdata.application;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class MarketDataEventPublisher {
    private final List<MarketDataPublishedEventPort> ports;

    MarketDataEventPublisher(List<MarketDataPublishedEventPort> ports) {
        this.ports = ports;
    }

    void publicTradeRecorded(String marketSymbol, String executionId) {
        ports.forEach(port -> port.publicTradeRecorded(marketSymbol, executionId));
    }

    void tickerUpdated(String marketSymbol) {
        ports.forEach(port -> port.tickerUpdated(marketSymbol));
    }

    void candleClosed(String marketSymbol, String intervalName) {
        ports.forEach(port -> port.candleClosed(marketSymbol, intervalName));
    }

    void orderBookUpdated(String marketSymbol, long sequence) {
        ports.forEach(port -> port.orderBookUpdated(marketSymbol, sequence));
    }

    void marketsUpdated() {
        ports.forEach(MarketDataPublishedEventPort::marketsUpdated);
    }
}
