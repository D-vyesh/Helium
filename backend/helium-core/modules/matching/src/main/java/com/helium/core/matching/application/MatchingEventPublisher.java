package com.helium.core.matching.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class MatchingEventPublisher {
    private final List<MatchingEventPort> eventPorts;

    MatchingEventPublisher(List<MatchingEventPort> eventPorts) {
        this.eventPorts = eventPorts;
    }

    void orderAccepted(MatchingEventPort.OrderAcceptedEvent event) {
        publishAfterCommit(() -> eventPorts.forEach(port -> port.orderAccepted(event)));
    }

    void orderCancelled(MatchingEventPort.OrderCancelledEvent event) {
        publishAfterCommit(() -> eventPorts.forEach(port -> port.orderCancelled(event)));
    }

    void orderExpired(MatchingEventPort.OrderExpiredEvent event) {
        publishAfterCommit(() -> eventPorts.forEach(port -> port.orderExpired(event)));
    }

    void executionCreated(MatchingEventPort.ExecutionCreatedEvent event) {
        publishAfterCommit(() -> eventPorts.forEach(port -> port.executionCreated(event)));
    }

    private void publishAfterCommit(Runnable operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            operation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                operation.run();
            }
        });
    }
}
