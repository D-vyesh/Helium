package com.helium.core.matching.application;

import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles matching engine events published through the outbox pattern.
 * Processes EXECUTION_CREATED, ORDER_ACCEPTED, and ORDER_CANCELLED events
 * to drive downstream trading settlement.
 */
@Component
public class MatchingOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(MatchingOutboxEventHandler.class);

    private final ObjectMapper objectMapper;
    private final MatchingExecutionLookupPort executionLookupPort;

    public MatchingOutboxEventHandler(ObjectMapper objectMapper, MatchingExecutionLookupPort executionLookupPort) {
        this.objectMapper = objectMapper;
        this.executionLookupPort = executionLookupPort;
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && (
            eventType.equals("MATCHING.EXECUTION_CREATED")
            || eventType.equals("MATCHING.ORDER_ACCEPTED")
            || eventType.equals("MATCHING.ORDER_CANCELLED")
        );
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            switch (message.eventType()) {
                case "MATCHING.EXECUTION_CREATED" -> handleExecution(payload, message);
                case "MATCHING.ORDER_ACCEPTED" -> handleOrderAccepted(payload, message);
                case "MATCHING.ORDER_CANCELLED" -> handleOrderCancelled(payload, message);
                default -> log.warn("Unhandled matching event type: {}", message.eventType());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to process matching outbox event " + message.id(), exception);
        }
    }

    private void handleExecution(JsonNode payload, OutboxMessage message) {
        String executionId = payload.path("executionId").asText();
        log.info("Processing matching execution via outbox: executionId={}, aggregateId={}",
            executionId, message.aggregateId());
    }

    private void handleOrderAccepted(JsonNode payload, OutboxMessage message) {
        log.info("Processing order accepted via outbox: orderId={}", message.aggregateId());
    }

    private void handleOrderCancelled(JsonNode payload, OutboxMessage message) {
        log.info("Processing order cancelled via outbox: orderId={}", message.aggregateId());
    }
}
