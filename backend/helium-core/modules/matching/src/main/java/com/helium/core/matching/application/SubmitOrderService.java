package com.helium.core.matching.application;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.Execution;
import com.helium.core.matching.domain.MarketMatchingState;
import com.helium.core.matching.domain.MarketMatchingStatus;
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

/**
 * Core matching engine: accepts orders and runs the price-time priority matching loop.
 *
 * <p>Supported order types:
 * <ul>
 *   <li><b>LIMIT</b>  — rest at limit price; fill any crossing resting orders; remainder rests on book (GTC/IOC/FOK/DAY).</li>
 *   <li><b>MARKET</b> — sweep the opposite side at best available prices; any unfilled remainder is
 *       immediately cancelled (market orders are always treated as IOC).</li>
 *   <li><b>POST_ONLY</b> — like LIMIT but rejected if it would immediately match (maker-only guarantee).</li>
 *   <li><b>STOP_LIMIT</b> — saved as STOP_PENDING; released into the active book after a trade at or
 *       beyond the stop price. After release, treated as a normal LIMIT order.</li>
 * </ul>
 */
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

        validateTypeAndPrice(orderType, command);

        lockService.lockMarket(marketSymbol);

        MarketMatchingState state = stateRepository.findById(marketSymbol)
            .orElseGet(() -> MarketMatchingState.start(marketSymbol, clock.instant()));
            
        if (state.status() == MarketMatchingStatus.HALTED) {
            throw new MatchingValidationException("market matching is halted");
        }
        if (state.status() == MarketMatchingStatus.AUCTION) {
            if (orderType == MatchingOrderType.MARKET || orderType == MatchingOrderType.POST_ONLY) {
                throw new MatchingValidationException("market and post_only orders not allowed during auction");
            }
        }

        String requestHash = MatchingHash.submissionHash(
            command.orderId(),
            marketSymbol,
            side,
            orderType,
            command.quantity(),
            effectiveLimitPrice(orderType, side, command.limitPrice()),
            command.stopPrice()
        );

        var existing = orderRepository.findByIdForUpdate(command.orderId());
        if (existing.isPresent()) {
            if (!existing.get().samePayload(requestHash)) {
                throw new MatchingValidationException("duplicate order submission payload differs");
            }
            return;
        }

        switch (orderType) {
            case LIMIT     -> submitLimit(command, marketSymbol, side, requestHash);
            case MARKET    -> submitMarket(command, marketSymbol, side, requestHash);
            case POST_ONLY -> submitPostOnly(command, marketSymbol, side, requestHash);
            case STOP_LIMIT -> submitStop(command, marketSymbol, side, requestHash);
        }
    }

    // ---- LIMIT ---------------------------------------------------------------

    private void submitLimit(
        MatchingCommandPort.SubmitOrderCommand command,
        String marketSymbol,
        MatchingOrderSide side,
        String requestHash
    ) {
        long acceptedSequence = sequenceService.next(marketSymbol);
        BookOrder taker = BookOrder.accept(
            command.orderId(), requestHash, marketSymbol, side,
            MatchingOrderType.LIMIT, command.quantity(), command.limitPrice(),
            acceptedSequence, clock.instant()
        );
        orderRepository.save(taker);
        recordState(marketSymbol, acceptedSequence);
        eventPublisher.orderAccepted(new MatchingEventPort.OrderAcceptedEvent(
            taker.orderId(), marketSymbol, acceptedSequence, taker.lastOrderOffset()));
        match(taker, false);
    }

    // ---- MARKET --------------------------------------------------------------

    /**
     * MARKET orders use a sentinel limit price so that {@code crosses()} always returns true:
     * <ul>
     *   <li>BUY  market → sentinel {@code Long.MAX_VALUE}-equivalent price ensures any ask is crossed.</li>
     *   <li>SELL market → sentinel {@code 0.000...01} price ensures any bid is crossed.</li>
     * </ul>
     * Any unfilled remainder is cancelled immediately (IOC).
     */
    private void submitMarket(
        MatchingCommandPort.SubmitOrderCommand command,
        String marketSymbol,
        MatchingOrderSide side,
        String requestHash
    ) {
        BigDecimal sentinelPrice = side == MatchingOrderSide.BUY
            ? new BigDecimal("999999999999999999")
            : new BigDecimal("0.000000000000000001");

        long acceptedSequence = sequenceService.next(marketSymbol);
        BookOrder taker = BookOrder.accept(
            command.orderId(), requestHash, marketSymbol, side,
            MatchingOrderType.MARKET, command.quantity(), sentinelPrice,
            acceptedSequence, clock.instant()
        );
        orderRepository.save(taker);
        recordState(marketSymbol, acceptedSequence);
        eventPublisher.orderAccepted(new MatchingEventPort.OrderAcceptedEvent(
            taker.orderId(), marketSymbol, acceptedSequence, taker.lastOrderOffset()));

        // Match, then cancel any unfilled remainder (IOC)
        match(taker, false);
        if (taker.matchable()) {
            long cancelSequence = sequenceService.next(marketSymbol);
            long cancelOffset = taker.cancel(clock.instant());
            orderRepository.save(taker);
            recordState(marketSymbol, cancelSequence);
            eventPublisher.orderCancelled(new MatchingEventPort.OrderCancelledEvent(
                taker.orderId(), marketSymbol, cancelSequence, cancelOffset));
        }
    }

    // ---- POST_ONLY -----------------------------------------------------------

    /**
     * POST_ONLY orders are rejected by the matching engine if they would immediately cross any
     * resting order — guaranteeing maker-fee economics for the submitter.
     * If the order does not cross it rests on the book like a normal LIMIT order.
     */
    private void submitPostOnly(
        MatchingCommandPort.SubmitOrderCommand command,
        String marketSymbol,
        MatchingOrderSide side,
        String requestHash
    ) {
        long acceptedSequence = sequenceService.next(marketSymbol);
        BookOrder taker = BookOrder.accept(
            command.orderId(), requestHash, marketSymbol, side,
            MatchingOrderType.POST_ONLY, command.quantity(), command.limitPrice(),
            acceptedSequence, clock.instant()
        );

        // Check whether this order would immediately cross any resting order
        MatchingOrderSide oppositeSide = side == MatchingOrderSide.BUY ? MatchingOrderSide.SELL : MatchingOrderSide.BUY;
        List<BookOrder> oppositeOrders = orderRepository.findMatchableForUpdate(marketSymbol, oppositeSide);
        boolean wouldCross = oppositeOrders.stream().anyMatch(taker::crosses);

        orderRepository.save(taker);
        recordState(marketSymbol, acceptedSequence);
        eventPublisher.orderAccepted(new MatchingEventPort.OrderAcceptedEvent(
            taker.orderId(), marketSymbol, acceptedSequence, taker.lastOrderOffset()));

        if (wouldCross) {
            long rejectSequence = sequenceService.next(marketSymbol);
            long rejectOffset = taker.reject(clock.instant());
            orderRepository.save(taker);
            recordState(marketSymbol, rejectSequence);
            eventPublisher.orderRejected(new MatchingEventPort.OrderRejectedEvent(
                taker.orderId(), marketSymbol, "post-only order would cross resting order",
                rejectSequence, rejectOffset));
        }
        // If it doesn't cross it rests on the book — no further action needed
    }

    // ---- STOP_LIMIT ----------------------------------------------------------

    /**
     * STOP_LIMIT orders rest as STOP_PENDING until a fill at or beyond the stop price triggers them.
     * After {@link #triggerStops(String, BigDecimal)} releases them, they become normal LIMIT orders.
     */
    private void submitStop(
        MatchingCommandPort.SubmitOrderCommand command,
        String marketSymbol,
        MatchingOrderSide side,
        String requestHash
    ) {
        long acceptedSequence = sequenceService.next(marketSymbol);
        BookOrder stopOrder = BookOrder.acceptStop(
            command.orderId(), requestHash, marketSymbol, side,
            command.quantity(), command.limitPrice(), command.stopPrice(),
            command.timeInForce(), acceptedSequence, clock.instant()
        );
        orderRepository.save(stopOrder);
        recordState(marketSymbol, acceptedSequence);
        eventPublisher.orderAccepted(new MatchingEventPort.OrderAcceptedEvent(
            stopOrder.orderId(), marketSymbol, acceptedSequence, stopOrder.lastOrderOffset()));
    }

    // ---- Core matching loop --------------------------------------------------

    /**
     * Sweeps the opposite side of the book filling as many orders as possible.
     *
     * <p><b>Matching algorithm: Price-Time (FIFO).</b> The {@link OrderBook} sorts
     * candidates by best price first, then by earliest {@code receivedSequence}.
     * The maker always sets the execution price. See {@link OrderBook} class Javadoc
     * for the full rationale and future extensibility notes regarding pro-rata.
     *
     * @param taker      the incoming order (already persisted and accepted)
     * @param resting    whether the taker is a previously-resting stop that was just released
     *                   (the opposite side is the taker's opposite side in either case)
     */
    private void match(BookOrder taker, boolean resting) {
        MarketMatchingState state = stateRepository.findById(taker.marketSymbol())
            .orElseGet(() -> MarketMatchingState.start(taker.marketSymbol(), clock.instant()));
        if (state.status() == MarketMatchingStatus.AUCTION) {
            return;
        }

        MatchingOrderSide oppositeSide = taker.side() == MatchingOrderSide.BUY
            ? MatchingOrderSide.SELL : MatchingOrderSide.BUY;
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
            BigDecimal executionPrice = maker.limitPrice(); // price-time priority: maker sets the price
            long executionSequence = sequenceService.next(taker.marketSymbol());

            BookOrder buyer  = taker.side() == MatchingOrderSide.BUY ? taker : maker;
            BookOrder seller = taker.side() == MatchingOrderSide.SELL ? taker : maker;
            long makerOffset = maker.fill(fillQuantity, clock.instant());
            long takerOffset = taker.fill(fillQuantity, clock.instant());
            long buyerOffset  = buyer.orderId().equals(maker.orderId())  ? makerOffset : takerOffset;
            long sellerOffset = seller.orderId().equals(maker.orderId()) ? makerOffset : takerOffset;

            Execution execution = Execution.create(
                taker.marketSymbol(),
                buyer.orderId(), seller.orderId(),
                maker.orderId(), taker.orderId(),
                fillQuantity, executionPrice,
                executionSequence, buyerOffset, sellerOffset,
                clock.instant()
            );
            orderRepository.save(maker);
            orderRepository.save(taker);
            executionRepository.save(execution);
            recordState(taker.marketSymbol(), executionSequence);
            eventPublisher.executionCreated(new MatchingEventPort.ExecutionCreatedEvent(
                execution.executionId(), execution.matchId(), taker.marketSymbol(),
                execution.buyerOrderId(), execution.sellerOrderId(),
                execution.makerOrderId(), execution.takerOrderId(),
                execution.quantity(), execution.price(),
                execution.sequenceNumber(),
                execution.buyerOrderOffset(), execution.sellerOrderOffset()
            ));

            // After each fill, check if any stop orders are triggered by this trade price
            triggerStops(taker.marketSymbol(), executionPrice);
        }
    }

    /**
     * Finds all STOP_LIMIT orders triggered by {@code lastTradePrice}, releases them into the
     * active book, and immediately runs the matching loop for each one.
     *
     * <p>Called after every fill. Stop orders released here may themselves trigger further fills
     * (cascading stops), but the recursion is bounded by the size of the STOP_PENDING set.
     */
    private void triggerStops(String marketSymbol, BigDecimal lastTradePrice) {
        List<BookOrder> triggered = orderRepository.findTriggeredStops(marketSymbol, lastTradePrice);
        for (BookOrder stop : triggered) {
            long releaseSequence = sequenceService.next(marketSymbol);
            stop.release(clock.instant());
            orderRepository.save(stop);
            recordState(marketSymbol, releaseSequence);
            // The stop is now ACTIVE — run the LIMIT matching loop for it
            match(stop, true);
            // If still matchable it rests on the book as a normal LIMIT order
        }
    }

    // ---- Helpers -------------------------------------------------------------

    @Transactional
    public void uncross(String marketSymbol) {
        List<BookOrder> bids = orderRepository.findMatchableForUpdate(marketSymbol, MatchingOrderSide.BUY);
        List<BookOrder> asks = orderRepository.findMatchableForUpdate(marketSymbol, MatchingOrderSide.SELL);

        OrderBook orderBook = new OrderBook(marketSymbol, bids, asks);
        BigDecimal equilibriumPrice = calculateEquilibriumPrice(bids, asks);
        
        if (equilibriumPrice == null) {
            return; // No overlapping orders
        }

        // Using a dummy order to fetch sorted bids from the order book
        List<BookOrder> sortedBids = orderBook.candidatesFor(BookOrder.accept(java.util.UUID.randomUUID(), "", marketSymbol, MatchingOrderSide.SELL, MatchingOrderType.LIMIT, BigDecimal.ONE, BigDecimal.ZERO, 0, clock.instant()));

        for (BookOrder bid : sortedBids) {
            if (!bid.matchable() || bid.limitPrice().compareTo(equilibriumPrice) < 0) continue;
            
            for (BookOrder ask : orderBook.candidatesFor(bid)) {
                if (!ask.matchable() || ask.limitPrice().compareTo(equilibriumPrice) > 0) continue;
                if (!bid.matchable()) break;

                BigDecimal fillQuantity = bid.remainingQuantity().min(ask.remainingQuantity()).stripTrailingZeros();
                if (fillQuantity.compareTo(BigDecimal.ZERO) <= 0) continue;

                long executionSequence = sequenceService.next(marketSymbol);
                long bidOffset = bid.fill(fillQuantity, clock.instant());
                long askOffset = ask.fill(fillQuantity, clock.instant());

                Execution execution = Execution.create(
                    marketSymbol,
                    bid.orderId(), ask.orderId(),
                    bid.orderId(), ask.orderId(), // both considered makers in an auction
                    fillQuantity, equilibriumPrice,
                    executionSequence, bidOffset, askOffset,
                    clock.instant()
                );

                orderRepository.save(bid);
                orderRepository.save(ask);
                executionRepository.save(execution);

                recordState(marketSymbol, executionSequence);

                eventPublisher.executionCreated(new MatchingEventPort.ExecutionCreatedEvent(
                    execution.executionId(), execution.matchId(), marketSymbol,
                    execution.buyerOrderId(), execution.sellerOrderId(),
                    execution.makerOrderId(), execution.takerOrderId(),
                    execution.quantity(), execution.price(),
                    execution.sequenceNumber(),
                    execution.buyerOrderOffset(), execution.sellerOrderOffset()
                ));
            }
        }
    }

    private BigDecimal calculateEquilibriumPrice(List<BookOrder> bids, List<BookOrder> asks) {
        java.util.Set<BigDecimal> candidatePrices = new java.util.HashSet<>();
        bids.forEach(b -> candidatePrices.add(b.limitPrice()));
        asks.forEach(a -> candidatePrices.add(a.limitPrice()));

        BigDecimal bestPrice = null;
        BigDecimal maxVolume = BigDecimal.ZERO;

        for (BigDecimal price : candidatePrices) {
            BigDecimal bidVol = bids.stream()
                .filter(b -> b.limitPrice().compareTo(price) >= 0)
                .map(BookOrder::remainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal askVol = asks.stream()
                .filter(a -> a.limitPrice().compareTo(price) <= 0)
                .map(BookOrder::remainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal executableVolume = bidVol.min(askVol);

            if (executableVolume.compareTo(BigDecimal.ZERO) > 0) {
                if (executableVolume.compareTo(maxVolume) > 0) {
                    maxVolume = executableVolume;
                    bestPrice = price;
                } else if (executableVolume.compareTo(maxVolume) == 0 && bestPrice != null) {
                    // Tie-breaker: pick the higher price for simplicity
                    if (price.compareTo(bestPrice) > 0) {
                        bestPrice = price;
                    }
                }
            }
        }

        return bestPrice;
    }

    private void recordState(String marketSymbol, long sequence) {
        MarketMatchingState state = stateRepository.findById(marketSymbol)
            .orElseGet(() -> MarketMatchingState.start(marketSymbol, clock.instant()));
        if (state.status() == MarketMatchingStatus.HALTED) {
            throw new MatchingValidationException("market matching is halted");
        }
        state.record(sequence, clock.instant());
        stateRepository.save(state);
    }

    private static void validateTypeAndPrice(MatchingOrderType orderType, MatchingCommandPort.SubmitOrderCommand command) {
        switch (orderType) {
            case LIMIT, POST_ONLY -> {
                if (command.limitPrice() == null) {
                    throw new MatchingValidationException(orderType.name().toLowerCase() + " order requires a limit price");
                }
            }
            case MARKET -> {
                // no price required — the engine uses a sentinel price internally
            }
            case STOP_LIMIT -> {
                if (command.limitPrice() == null) {
                    throw new MatchingValidationException("stop-limit order requires a limit price");
                }
                if (command.stopPrice() == null) {
                    throw new MatchingValidationException("stop-limit order requires a stop price");
                }
            }
        }
    }

    /**
     * Returns the canonical limit price for hashing purposes.
     * MARKET orders store a known sentinel rather than null so the hash is stable.
     */
    private static BigDecimal effectiveLimitPrice(MatchingOrderType orderType, MatchingOrderSide side, BigDecimal limitPrice) {
        if (orderType == MatchingOrderType.MARKET) {
            return side == MatchingOrderSide.BUY
                ? new BigDecimal("999999999999999999")
                : new BigDecimal("0.000000000000000001");
        }
        return limitPrice;
    }
}
