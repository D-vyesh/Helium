package com.helium.core.trading.application;

import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles settlement-related events published through the outbox pattern.
 * Processes TRADE_SETTLED and SETTLEMENT_FAILED events for downstream
 * notifications and reconciliation.
 */
@Component
public class SettlementOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(SettlementOutboxEventHandler.class);

    private final ObjectMapper objectMapper;

    public SettlementOutboxEventHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && (
            eventType.equals("TRADING.TRADE_SETTLED")
            || eventType.equals("TRADING.SETTLEMENT_FAILED")
        );
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            switch (message.eventType()) {
                case "TRADING.TRADE_SETTLED" -> handleTradeSettled(payload, message);
                case "TRADING.SETTLEMENT_FAILED" -> handleSettlementFailed(payload, message);
                default -> log.warn("Unhandled trading event type: {}", message.eventType());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to process settlement outbox event " + message.id(), exception);
        }
    }

    private void handleTradeSettled(JsonNode payload, OutboxMessage message) {
        log.info("Trade settled via outbox: executionId={}, aggregateId={}",
            payload.path("executionId").asText(), message.aggregateId());
    }

    private void handleSettlementFailed(JsonNode payload, OutboxMessage message) {
        log.error("Settlement failed via outbox: executionId={}, error={}",
            payload.path("executionId").asText(), payload.path("error").asText());
    }
}
