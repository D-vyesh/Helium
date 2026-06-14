package com.helium.core.matching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.Execution;
import com.helium.core.matching.domain.MatchingOrderSide;
import com.helium.core.matching.domain.MatchingOrderType;
import com.helium.core.matching.domain.MatchingValidationException;
import com.helium.core.matching.infrastructure.BookOrderRepository;
import com.helium.core.matching.infrastructure.ExecutionRepository;
import com.helium.core.matching.infrastructure.MarketMatchingStateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class SubmitOrderServiceTest {
    private final BookOrderRepository orderRepository = mock(BookOrderRepository.class);
    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final MarketMatchingStateRepository stateRepository = mock(MarketMatchingStateRepository.class);
    private final MatchingSequenceService sequenceService = mock(MatchingSequenceService.class);
    private final MatchingAdvisoryLockService lockService = mock(MatchingAdvisoryLockService.class);
    private final MatchingEventPublisher eventPublisher = mock(MatchingEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final SubmitOrderService service = new SubmitOrderService(
        orderRepository,
        executionRepository,
        stateRepository,
        sequenceService,
        lockService,
        eventPublisher,
        clock
    );

    @Test
    void matchesAgainstBestPriceThenOldestTime() {
        UUID takerId = UUID.randomUUID();
        BookOrder worseAsk = resting(UUID.randomUUID(), MatchingOrderSide.SELL, "101.00", 2);
        BookOrder bestAsk = resting(UUID.randomUUID(), MatchingOrderSide.SELL, "100.00", 1);
        when(orderRepository.findByIdForUpdate(takerId)).thenReturn(Optional.empty());
        when(orderRepository.findMatchableForUpdate("BTC-USD", MatchingOrderSide.SELL)).thenReturn(List.of(worseAsk, bestAsk));
        when(sequenceService.next("BTC-USD")).thenReturn(3L, 4L);

        service.submit(limit(takerId, "BUY", "1.0", "101.00"));

        ArgumentCaptor<Execution> execution = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(execution.capture());
        assertThat(execution.getValue().sellerOrderId()).isEqualTo(bestAsk.orderId());
        assertThat(execution.getValue().price()).isEqualByComparingTo("100.00");
        assertThat(execution.getValue().makerOrderId()).isEqualTo(bestAsk.orderId());
        assertThat(execution.getValue().takerOrderId()).isEqualTo(takerId);
    }

    @Test
    void partiallyFillsRestingOrderAndLeavesRemainingTakerOnBook() {
        UUID takerId = UUID.randomUUID();
        BookOrder ask = resting(UUID.randomUUID(), MatchingOrderSide.SELL, "100.00", 1);
        ask.fill(new BigDecimal("0.6"), clock.instant());
        when(orderRepository.findByIdForUpdate(takerId)).thenReturn(Optional.empty());
        when(orderRepository.findMatchableForUpdate("BTC-USD", MatchingOrderSide.SELL)).thenReturn(List.of(ask));
        when(sequenceService.next("BTC-USD")).thenReturn(2L, 3L);

        service.submit(limit(takerId, "BUY", "1.0", "100.00"));

        ArgumentCaptor<BookOrder> savedOrders = ArgumentCaptor.forClass(BookOrder.class);
        verify(orderRepository, org.mockito.Mockito.atLeastOnce()).save(savedOrders.capture());
        BookOrder taker = savedOrders.getAllValues().stream()
            .filter(order -> order.orderId().equals(takerId))
            .reduce((first, second) -> second)
            .orElseThrow();
        assertThat(taker.remainingQuantity()).isEqualByComparingTo("0.6");
        assertThat(ask.remainingQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void duplicateSubmissionWithSamePayloadIsSafeReplay() {
        UUID orderId = UUID.randomUUID();
        String hash = MatchingHash.submissionHash(orderId, "BTC-USD", MatchingOrderSide.BUY, MatchingOrderType.LIMIT, new BigDecimal("1.0"), new BigDecimal("100.00"));
        BookOrder existing = BookOrder.accept(orderId, hash, "BTC-USD", MatchingOrderSide.BUY, MatchingOrderType.LIMIT, new BigDecimal("1.0"), new BigDecimal("100.00"), 1, clock.instant());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(existing));

        service.submit(limit(orderId, "BUY", "1.0", "100.00"));

        verify(executionRepository, never()).save(any());
        verify(eventPublisher, never()).orderAccepted(any());
    }

    @Test
    void duplicateSubmissionWithDifferentPayloadFailsClosed() {
        UUID orderId = UUID.randomUUID();
        String hash = MatchingHash.submissionHash(orderId, "BTC-USD", MatchingOrderSide.BUY, MatchingOrderType.LIMIT, new BigDecimal("1.0"), new BigDecimal("100.00"));
        BookOrder existing = BookOrder.accept(orderId, hash, "BTC-USD", MatchingOrderSide.BUY, MatchingOrderType.LIMIT, new BigDecimal("1.0"), new BigDecimal("100.00"), 1, clock.instant());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submit(limit(orderId, "BUY", "2.0", "100.00")))
            .isInstanceOf(MatchingValidationException.class)
            .hasMessageContaining("payload differs");
    }

    @Test
    void locksMarketBeforeReadingOrWritingBookState() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findMatchableForUpdate("BTC-USD", MatchingOrderSide.SELL)).thenReturn(List.of());
        when(sequenceService.next("BTC-USD")).thenReturn(1L);

        service.submit(limit(orderId, "BUY", "1.0", "100.00"));

        InOrder ordered = inOrder(lockService, orderRepository);
        ordered.verify(lockService).lockMarket("BTC-USD");
        ordered.verify(orderRepository).findByIdForUpdate(orderId);
    }

    private static MatchingCommandPort.SubmitOrderCommand limit(UUID orderId, String side, String quantity, String price) {
        return new MatchingCommandPort.SubmitOrderCommand(
            orderId,
            "BTC-USD",
            side,
            "LIMIT",
            "GTC",
            new BigDecimal(quantity),
            new BigDecimal(price)
        );
    }

    private BookOrder resting(UUID orderId, MatchingOrderSide side, String price, long sequence) {
        return BookOrder.accept(
            orderId,
            "0".repeat(64),
            "BTC-USD",
            side,
            MatchingOrderType.LIMIT,
            new BigDecimal("1.0"),
            new BigDecimal(price),
            sequence,
            clock.instant()
        );
    }
}
