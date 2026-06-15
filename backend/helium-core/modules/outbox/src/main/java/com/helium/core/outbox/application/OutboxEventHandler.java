package com.helium.core.outbox.application;

public interface OutboxEventHandler {
    boolean supports(String eventType);

    void handle(OutboxMessage message);
}
