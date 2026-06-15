package com.helium.core.outbox.application;

public interface OutboxPublisher {
    void publish(String aggregateType, String aggregateId, String eventType, String payload);
}
