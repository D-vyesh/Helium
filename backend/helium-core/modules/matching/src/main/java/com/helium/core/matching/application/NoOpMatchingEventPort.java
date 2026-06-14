package com.helium.core.matching.application;

import org.springframework.stereotype.Component;

@Component
class NoOpMatchingEventPort implements MatchingEventPort {
    @Override
    public void orderAccepted(OrderAcceptedEvent event) {
    }

    @Override
    public void orderCancelled(OrderCancelledEvent event) {
    }

    @Override
    public void orderExpired(OrderExpiredEvent event) {
    }

    @Override
    public void executionCreated(ExecutionCreatedEvent event) {
    }
}
