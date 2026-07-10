package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderAuditRecord;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.infrastructure.OrderRepository;
import com.helium.core.trading.infrastructure.OrderAuditRecordRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class TradingAuditPublisher {
    private final OrderAuditRecordRepository repository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    TradingAuditPublisher(
        OrderAuditRecordRepository repository,
        OrderRepository orderRepository,
        ApplicationEventPublisher eventPublisher,
        Clock clock
    ) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void publish(UUID orderId, OrderStatus status, String actorId, String details) {
        OrderAuditRecord record = OrderAuditRecord.record(orderId, status, actorId, details, clock.instant());
        repository.save(record);
        orderRepository.findById(orderId)
            .ifPresent(order -> eventPublisher.publishEvent(new TradingOrderChangedEvent(order.id(), order.userId(), order.status())));
    }
}
