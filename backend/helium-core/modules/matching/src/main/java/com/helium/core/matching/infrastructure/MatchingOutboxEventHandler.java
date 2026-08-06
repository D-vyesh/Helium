package com.helium.core.matching.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.matching.application.MatchingCommandPort;
import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MatchingOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(MatchingOutboxEventHandler.class);

    private final ObjectMapper objectMapper;
    private final MatchingCommandPort matchingCommandPort;

    public MatchingOutboxEventHandler(ObjectMapper objectMapper, MatchingCommandPort matchingCommandPort) {
        this.objectMapper = objectMapper;
        this.matchingCommandPort = matchingCommandPort;
    }

    @Override
    public boolean supports(String eventType) {
        return "TRADING.MARKET_HALTED".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            String marketSymbol = payload.path("marketSymbol").asText();
            
            if (marketSymbol == null || marketSymbol.isEmpty()) {
                log.error("Missing marketSymbol in TRADING.MARKET_HALTED payload");
                return;
            }

            log.info("Received MARKET_HALTED event for market {}. Halting matching engine.", marketSymbol);
            matchingCommandPort.haltMarket(new MatchingCommandPort.HaltMarketCommand(marketSymbol));
        } catch (Exception exception) {
            throw new RuntimeException("Failed to process TRADING.MARKET_HALTED outbox event " + message.id(), exception);
        }
    }
}
