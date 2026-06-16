package com.helium.core.marketdata.application;

import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles market data events published through the outbox pattern.
 * Processes ticker updates, orderbook snapshots, and trade events for
 * reliable fanout to WebSocket subscribers and downstream projections.
 */
@Component
public class MarketDataOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(MarketDataOutboxEventHandler.class);

    private final ObjectMapper objectMapper;
    private final WebSocketBroadcaster webSocketBroadcaster;

    public MarketDataOutboxEventHandler(ObjectMapper objectMapper, WebSocketBroadcaster webSocketBroadcaster) {
        this.objectMapper = objectMapper;
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && (
            eventType.equals("MARKET_DATA.TICKER_UPDATED")
            || eventType.equals("MARKET_DATA.ORDERBOOK_SNAPSHOT")
            || eventType.equals("MARKET_DATA.TRADE_EXECUTED")
        );
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            String market = message.aggregateId();
            switch (message.eventType()) {
                case "MARKET_DATA.TICKER_UPDATED" -> {
                    webSocketBroadcaster.broadcast(market + ":ticker", payload);
                    log.debug("Ticker update broadcast via outbox: market={}", market);
                }
                case "MARKET_DATA.ORDERBOOK_SNAPSHOT" -> {
                    webSocketBroadcaster.broadcast(market + ":orderbook", payload);
                    log.debug("Orderbook snapshot broadcast via outbox: market={}", market);
                }
                case "MARKET_DATA.TRADE_EXECUTED" -> {
                    webSocketBroadcaster.broadcast(market + ":trade", payload);
                    log.debug("Trade executed broadcast via outbox: market={}", market);
                }
                default -> log.warn("Unhandled market data event type: {}", message.eventType());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to process market data outbox event " + message.id(), exception);
        }
    }
}
