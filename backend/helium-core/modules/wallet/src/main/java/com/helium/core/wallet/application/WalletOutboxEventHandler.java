package com.helium.core.wallet.application;

import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles wallet/chain events published through the outbox pattern.
 * Processes deposit confirmations, withdrawal broadcasts, and withdrawal confirmations
 * for reliable notification delivery and reconciliation.
 */
@Component
public class WalletOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(WalletOutboxEventHandler.class);

    private final ObjectMapper objectMapper;

    public WalletOutboxEventHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && (
            eventType.equals("WALLET.DEPOSIT_CONFIRMED")
            || eventType.equals("WALLET.WITHDRAWAL_BROADCAST")
            || eventType.equals("WALLET.WITHDRAWAL_CONFIRMED")
        );
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            switch (message.eventType()) {
                case "WALLET.DEPOSIT_CONFIRMED" -> handleDepositConfirmed(payload, message);
                case "WALLET.WITHDRAWAL_BROADCAST" -> handleWithdrawalBroadcast(payload, message);
                case "WALLET.WITHDRAWAL_CONFIRMED" -> handleWithdrawalConfirmed(payload, message);
                default -> log.warn("Unhandled wallet event type: {}", message.eventType());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to process wallet outbox event " + message.id(), exception);
        }
    }

    private void handleDepositConfirmed(JsonNode payload, OutboxMessage message) {
        log.info("Deposit confirmed via outbox: depositId={}, userId={}, amount={}",
            message.aggregateId(), payload.path("userId").asText(), payload.path("amount").asText());
    }

    private void handleWithdrawalBroadcast(JsonNode payload, OutboxMessage message) {
        log.info("Withdrawal broadcast via outbox: withdrawalId={}, txHash={}",
            message.aggregateId(), payload.path("txHash").asText());
    }

    private void handleWithdrawalConfirmed(JsonNode payload, OutboxMessage message) {
        log.info("Withdrawal confirmed via outbox: withdrawalId={}, confirmations={}",
            message.aggregateId(), payload.path("confirmations").asInt());
    }
}
