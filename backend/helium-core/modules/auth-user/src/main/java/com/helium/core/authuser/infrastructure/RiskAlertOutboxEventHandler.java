package com.helium.core.authuser.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.authuser.application.AccountAdministrationPort;
import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RiskAlertOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(RiskAlertOutboxEventHandler.class);

    private final ObjectMapper objectMapper;
    private final AccountAdministrationPort accountAdministrationPort;

    public RiskAlertOutboxEventHandler(ObjectMapper objectMapper, AccountAdministrationPort accountAdministrationPort) {
        this.objectMapper = objectMapper;
        this.accountAdministrationPort = accountAdministrationPort;
    }

    @Override
    public boolean supports(String eventType) {
        return "TRADING.RISK_ALERT".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            String type = payload.path("type").asText();
            String userIdStr = payload.path("userId").asText();

            if ("WASH_TRADING".equals(type)) {
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    UUID userId = UUID.fromString(userIdStr);
                    String marketId = payload.path("marketId").asText("");
                    log.info("Suspending account {} due to wash trading detection in market {}", userId, marketId);
                    accountAdministrationPort.suspendBySystem(userId, "System: Wash trading detected in market " + marketId);
                } else {
                    log.error("Missing userId in TRADING.RISK_ALERT wash trading payload");
                }
            } else if ("SPOOFING".equals(type)) {
                // Future enhancement: could increment a spoofing strike counter and suspend after 3 strikes
                log.info("Spoofing alert received for user {}, no automatic suspension applied.", userIdStr);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to process TRADING.RISK_ALERT outbox event " + message.id(), exception);
        }
    }
}
