package com.helium.core.matching.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface MatchingEventPort {
    void orderAccepted(OrderAcceptedEvent event);

    void orderCancelled(OrderCancelledEvent event);

    void orderExpired(OrderExpiredEvent event);

    void orderRejected(OrderRejectedEvent event);

    void executionCreated(ExecutionCreatedEvent event);

    record OrderAcceptedEvent(UUID orderId, String marketSymbol, long marketSequence, long orderOffset) {
    }

    record OrderCancelledEvent(UUID orderId, String marketSymbol, long marketSequence, long orderOffset) {
    }

    record OrderExpiredEvent(UUID orderId, String marketSymbol, long marketSequence, long orderOffset) {
    }

    /**
     * Emitted when the matching engine rejects an order without filling it.
     * Used for POST_ONLY orders that would immediately cross and for any other
     * engine-level rejection reasons.
     */
    record OrderRejectedEvent(UUID orderId, String marketSymbol, String reason, long marketSequence, long orderOffset) {
    }

    record ExecutionCreatedEvent(
        String executionId,
        String matchId,
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal quantity,
        BigDecimal price,
        long sequence,
        long buyerOrderOffset,
        long sellerOrderOffset
    ) {
    }
}
