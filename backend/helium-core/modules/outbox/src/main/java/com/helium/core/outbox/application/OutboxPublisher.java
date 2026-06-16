package com.helium.core.outbox.application;

/**
 * Publishes events to the transactional outbox for reliable at-least-once delivery.
 * Implementations insert into outbox_events within the current transaction.
 */
public interface OutboxPublisher {
    /**
     * Publish an event to the outbox.
     */
    void publish(String aggregateType, String aggregateId, String eventType, String payload);

    /**
     * Publish an event with exactly-once deduplication guarantee.
     * If a message with the same deduplication key already exists, it is silently ignored.
     *
     * @param deduplicationKey unique key to prevent duplicate publishing
     * @return true if the event was inserted, false if it was a duplicate
     */
    boolean publishWithDeduplication(String deduplicationKey, String aggregateType, String aggregateId, String eventType, String payload);
}
