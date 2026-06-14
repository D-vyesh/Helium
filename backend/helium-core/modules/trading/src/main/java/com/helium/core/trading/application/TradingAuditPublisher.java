package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderAuditRecord;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.infrastructure.OrderAuditRecordRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class TradingAuditPublisher {
    private final OrderAuditRecordRepository repository;
    private final Clock clock;

    TradingAuditPublisher(OrderAuditRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void publish(UUID orderId, OrderStatus status, String actorId, String details) {
        OrderAuditRecord record = OrderAuditRecord.record(orderId, status, actorId, details, clock.instant());
        repository.save(record);
    }
}
