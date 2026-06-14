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
public class OrderExpirationService {
    private final OrderRepository orderRepository;
    private final ReservationReleaseService releaseService;
    private final TradingAuditPublisher auditPublisher;
    private final TrustedMatchingActorProvider matchingActorProvider;
    private final Clock clock;

    public OrderExpirationService(
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
    public void expireOrder(UUID orderId, long matchingOffset) {
        matchingActorProvider.requireMatchingEngine();
        String actorId = matchingActorProvider.matchingActorId();
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new TradingValidationException("order not found"));

        order.expire(matchingOffset, clock.instant());
        orderRepository.save(order);

        releaseService.releaseRemaining(
            order,
            "expiration",
            "trading:release:" + order.id() + ":expire",
            actorId
        );

        auditPublisher.publish(order.id(), order.status(), actorId, "Order expired");
    }
}
