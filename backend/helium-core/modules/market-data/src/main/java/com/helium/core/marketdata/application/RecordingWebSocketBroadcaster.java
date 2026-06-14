package com.helium.core.marketdata.application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class RecordingWebSocketBroadcaster implements WebSocketBroadcaster, MarketDataPublishedEventPort {
    private final List<Broadcast> broadcasts = new CopyOnWriteArrayList<>();

    @Override
    public void broadcast(String topic, Object payload) {
        broadcasts.add(new Broadcast(topic, payload));
    }

    @Override
    public void publicTradeRecorded(String marketSymbol, String executionId) {
        broadcast("/trades." + marketSymbol, executionId);
    }

    @Override
    public void tickerUpdated(String marketSymbol) {
        broadcast("/ticker." + marketSymbol, marketSymbol);
    }

    @Override
    public void candleClosed(String marketSymbol, String intervalName) {
        broadcast("/candles." + marketSymbol + "." + intervalName, marketSymbol);
    }

    @Override
    public void orderBookUpdated(String marketSymbol, long sequence) {
        broadcast("/book." + marketSymbol + ".delta", sequence);
    }

    @Override
    public void marketsUpdated() {
        broadcast("/markets", "markets");
    }

    public List<Broadcast> broadcasts() {
        return List.copyOf(broadcasts);
    }

    public void clear() {
        broadcasts.clear();
    }

    public record Broadcast(String topic, Object payload) {
    }
}
