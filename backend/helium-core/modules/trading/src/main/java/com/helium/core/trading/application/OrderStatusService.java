package com.helium.core.trading.application;

import com.helium.core.matching.application.TrustedMatchingActorProvider;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.OrderRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStatusService {
    private final OrderRepository orderRepository;
    private final ReservationReleaseService releaseService;
    private final TradingAuditPublisher auditPublisher;
    private final TrustedMatchingActorProvider matchingActorProvider;
    private final Clock clock;

    public OrderStatusService(
        OrderRepository orderRepository,
        ReservationReleaseService releaseService,
        TradingAuditPublisher auditPublisher,
        TrustedMatchingActorProvider matchingActorProvider,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.releaseService = releaseService;
        this.auditPublisher = auditPublisher;
        this.matchingActorProvider = matchingActorProvider;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAccepted(UUID orderId, long matchingOffset) {
        matchingActorProvider.requireMatchingEngine();
        String actorId = matchingActorProvider.matchingActorId();
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new TradingValidationException("order not found"));

        order.accept(matchingOffset, clock.instant());
        orderRepository.save(order);

        auditPublisher.publish(order.id(), order.status(), actorId, "Order accepted by matching");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(UUID orderId, long matchingOffset) {
        matchingActorProvider.requireMatchingEngine();
        String actorId = matchingActorProvider.matchingActorId();
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new TradingValidationException("order not found"));

        order.cancel(matchingOffset, clock.instant());
        orderRepository.save(order);

        releaseService.releaseRemaining(
            order,
            "cancellation",
            "trading:release:" + order.id() + ":cancel",
            actorId
        );
        auditPublisher.publish(order.id(), order.status(), actorId, "Order cancelled");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRejected(UUID orderId, String reason, long matchingOffset) {
        matchingActorProvider.requireMatchingEngine();
        String actorId = matchingActorProvider.matchingActorId();
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new TradingValidationException("order not found"));

        order.reject(reason, matchingOffset, clock.instant());
        orderRepository.save(order);

        releaseService.releaseRemaining(
            order,
            "rejection",
            "trading:release:" + order.id() + ":cancel",
            actorId
        );
        auditPublisher.publish(order.id(), order.status(), actorId, "Order rejected: " + reason);
    }
}
