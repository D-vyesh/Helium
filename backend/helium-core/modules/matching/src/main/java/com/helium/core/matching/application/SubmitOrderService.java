package com.helium.core.matching.application;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.Execution;
import com.helium.core.matching.domain.MarketMatchingState;
import com.helium.core.matching.domain.MatchingOrderSide;
import com.helium.core.matching.domain.MatchingOrderType;
import com.helium.core.matching.domain.MatchingText;
import com.helium.core.matching.domain.MatchingValidationException;
import com.helium.core.matching.domain.OrderBook;
import com.helium.core.matching.infrastructure.BookOrderRepository;
import com.helium.core.matching.infrastructure.ExecutionRepository;
import com.helium.core.matching.infrastructure.MarketMatchingStateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitOrderService {
    private final BookOrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final MarketMatchingStateRepository stateRepository;
    private final MatchingSequenceService sequenceService;
    private final MatchingAdvisoryLockService lockService;
    private final MatchingEventPublisher eventPublisher;
    private final Clock clock;

    public SubmitOrderService(
        BookOrderRepository orderRepository,
        ExecutionRepository executionRepository,
        MarketMatchingStateRepository stateRepository,
        MatchingSequenceService sequenceService,
        MatchingAdvisoryLockService lockService,
        MatchingEventPublisher eventPublisher,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.executionRepository = executionRepository;
        this.stateRepository = stateRepository;
        this.sequenceService = sequenceService;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void submit(MatchingCommandPort.SubmitOrderCommand command) {
        String marketSymbol = MatchingText.market(command.marketSymbol());
        MatchingOrderSide side = MatchingOrderSide.valueOf(command.side());
        MatchingOrderType orderType = MatchingOrderType.valueOf(command.orderType());
        if (orderType != MatchingOrderType.LIMIT) {
            throw new MatchingValidationException("only limit orders are supported");
        }
        if (command.limitPrice() == null) {
            throw new MatchingValidationException("limit order requires price");
        }

        lockService.lockMarket(marketSymbol);

        String requestHash = MatchingHash.submissionHash(
            command.orderId(),
            marketSymbol,
            side,
            orderType,
            command.quantity(),
            command.limitPrice()
        );
        var existing = orderRepository.findByIdForUpdate(command.orderId());
        if (existing.isPresent()) {
            if (!existing.get().samePayload(requestHash)) {
                throw new MatchingValidationException("duplicate order submission payload differs");
            }
            return;
        }

        long acceptedSequence = sequenceService.next(marketSymbol);
        BookOrder taker = BookOrder.accept(
            command.orderId(),
            requestHash,
            marketSymbol,
            side,
            orderType,
            command.quantity(),
            command.limitPrice(),
            acceptedSequence,
            clock.instant()
        );
        orderRepository.save(taker);
        recordState(marketSymbol, acceptedSequence);
        eventPublisher.orderAccepted(new MatchingEventPort.OrderAcceptedEvent(taker.orderId(), marketSymbol, acceptedSequence, taker.lastOrderOffset()));

        match(taker);
    }

    private void match(BookOrder taker) {
        MatchingOrderSide oppositeSide = taker.side() == MatchingOrderSide.BUY ? MatchingOrderSide.SELL : MatchingOrderSide.BUY;
        List<BookOrder> oppositeOrders = orderRepository.findMatchableForUpdate(taker.marketSymbol(), oppositeSide);
        OrderBook orderBook = new OrderBook(
            taker.marketSymbol(),
            taker.side() == MatchingOrderSide.BUY ? List.of(taker) : oppositeOrders,
            taker.side() == MatchingOrderSide.SELL ? List.of(taker) : oppositeOrders
        );

        for (BookOrder maker : orderBook.candidatesFor(taker)) {
            if (!taker.matchable() || !taker.crosses(maker)) {
                break;
            }
            BigDecimal fillQuantity = taker.remainingQuantity().min(maker.remainingQuantity()).stripTrailingZeros();
            long executionSequence = sequenceService.next(taker.marketSymbol());
            BookOrder buyer = taker.side() == MatchingOrderSide.BUY ? taker : maker;
            BookOrder seller = taker.side() == MatchingOrderSide.SELL ? taker : maker;
            long makerOffset = maker.fill(fillQuantity, clock.instant());
            long takerOffset = taker.fill(fillQuantity, clock.instant());
            long buyerOffset = buyer.orderId().equals(maker.orderId()) ? makerOffset : takerOffset;
            long sellerOffset = seller.orderId().equals(maker.orderId()) ? makerOffset : takerOffset;
            Execution execution = Execution.create(
                taker.marketSymbol(),
                buyer.orderId(),
                seller.orderId(),
                maker.orderId(),
                taker.orderId(),
                fillQuantity,
                maker.limitPrice(),
                executionSequence,
                buyerOffset,
                sellerOffset,
                clock.instant()
            );
            orderRepository.save(maker);
            orderRepository.save(taker);
            executionRepository.save(execution);
            recordState(taker.marketSymbol(), executionSequence);
            eventPublisher.executionCreated(new MatchingEventPort.ExecutionCreatedEvent(
                execution.executionId(),
                execution.matchId(),
                taker.marketSymbol(),
                execution.buyerOrderId(),
                execution.sellerOrderId(),
                execution.makerOrderId(),
                execution.takerOrderId(),
                execution.quantity(),
                execution.price(),
                execution.sequenceNumber(),
                execution.buyerOrderOffset(),
                execution.sellerOrderOffset()
            ));
        }
    }

    private void recordState(String marketSymbol, long sequence) {
        MarketMatchingState state = stateRepository.findById(marketSymbol)
            .orElseGet(() -> MarketMatchingState.start(marketSymbol, clock.instant()));
        if (!state.active()) {
            throw new MatchingValidationException("market matching is halted");
        }
        state.record(sequence, clock.instant());
        stateRepository.save(state);
    }
}
