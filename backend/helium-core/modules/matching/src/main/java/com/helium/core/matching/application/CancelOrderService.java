package com.helium.core.matching.application;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.MatchingText;
import com.helium.core.matching.domain.MatchingValidationException;
import com.helium.core.matching.infrastructure.BookOrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelOrderService {
    private final BookOrderRepository orderRepository;
    private final MatchingSequenceService sequenceService;
    private final MatchingAdvisoryLockService lockService;
    private final MatchingEventPublisher eventPublisher;
    private final Clock clock;

    public CancelOrderService(
        BookOrderRepository orderRepository,
        MatchingSequenceService sequenceService,
        MatchingAdvisoryLockService lockService,
        MatchingEventPublisher eventPublisher,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.sequenceService = sequenceService;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void cancel(MatchingCommandPort.CancelOrderCommand command) {
        String marketSymbol = MatchingText.market(command.marketSymbol());
        lockService.lockMarket(marketSymbol);
        BookOrder order = orderRepository.findByIdForUpdate(command.orderId())
            .orElseThrow(() -> new MatchingValidationException("matching order not found"));
        if (!order.marketSymbol().equals(marketSymbol)) {
            throw new MatchingValidationException("cancel market does not match order market");
        }
        if (!order.matchable()) {
            return;
        }
        long sequence = sequenceService.next(marketSymbol);
        long orderOffset = order.cancel(clock.instant());
        orderRepository.save(order);
        eventPublisher.orderCancelled(new MatchingEventPort.OrderCancelledEvent(order.orderId(), marketSymbol, sequence, orderOffset));
    }

    @Transactional
    public void expire(MatchingCommandPort.ExpireOrderCommand command) {
        String marketSymbol = MatchingText.market(command.marketSymbol());
        lockService.lockMarket(marketSymbol);
        BookOrder order = orderRepository.findByIdForUpdate(command.orderId())
            .orElseThrow(() -> new MatchingValidationException("matching order not found"));
        if (!order.marketSymbol().equals(marketSymbol)) {
            throw new MatchingValidationException("expire market does not match order market");
        }
        if (!order.matchable()) {
            return;
        }
        long sequence = sequenceService.next(marketSymbol);
        long orderOffset = order.expire(clock.instant());
        orderRepository.save(order);
        eventPublisher.orderExpired(new MatchingEventPort.OrderExpiredEvent(order.orderId(), marketSymbol, sequence, orderOffset));
    }
}
