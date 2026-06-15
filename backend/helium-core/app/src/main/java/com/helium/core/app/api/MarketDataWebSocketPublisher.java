package com.helium.core.app.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.marketdata.application.MarketDataPublishedEventPort;
import org.springframework.stereotype.Component;

@Component
public class MarketDataWebSocketPublisher implements MarketDataPublishedEventPort {
    private final MarketDataWebSocketHandler handler;
    private final RedisInfrastructureService redis;
    private final ObjectMapper objectMapper;

    public MarketDataWebSocketPublisher(
        MarketDataWebSocketHandler handler,
        RedisInfrastructureService redis,
        ObjectMapper objectMapper
    ) {
        this.handler = handler;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publicTradeRecorded(String marketSymbol, String executionId) {
        broadcast(marketSymbol.toUpperCase() + "/TRADES", "PublicTradeRecorded", new TradePayload(marketSymbol, executionId));
    }

    @Override
    public void tickerUpdated(String marketSymbol) {
        broadcast(marketSymbol.toUpperCase() + "/TICKER", "TickerUpdated", new MarketPayload(marketSymbol));
    }

    @Override
    public void candleClosed(String marketSymbol, String intervalName) {
        broadcast(marketSymbol.toUpperCase() + "/TICKER", "CandleClosed", new CandlePayload(marketSymbol, intervalName));
    }

    @Override
    public void orderBookUpdated(String marketSymbol, long sequence) {
        broadcast(marketSymbol.toUpperCase() + "/ORDERBOOK", "OrderBookUpdated", new BookPayload(marketSymbol, sequence));
    }

    @Override
    public void marketsUpdated() {
        broadcast("MARKETS", "MarketsUpdated", null);
    }

    private void broadcast(String topic, String eventType, Object payload) {
        handler.broadcast(topic, eventType, payload);
        redis.publishWebSocketFanout(topic, toJson(new FanoutPayload(eventType, payload)));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize websocket fanout payload", exception);
        }
    }

    private record FanoutPayload(String eventType, Object payload) {}
    private record MarketPayload(String market) {}
    private record TradePayload(String market, String executionId) {}
    private record CandlePayload(String market, String interval) {}
    private record BookPayload(String market, long sequence) {}
}
