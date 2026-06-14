package com.helium.core.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderBookTest {
    @Test
    void sortsByStrictPriceTimePriority() {
        BookOrder olderAsk = order(MatchingOrderSide.SELL, "100.00", 2);
        BookOrder newerAskSamePrice = order(MatchingOrderSide.SELL, "100.00", 3);
        BookOrder betterAsk = order(MatchingOrderSide.SELL, "99.00", 4);
        BookOrder taker = order(MatchingOrderSide.BUY, "101.00", 5);

        OrderBook book = new OrderBook("BTC-USD", List.of(taker), List.of(newerAskSamePrice, betterAsk, olderAsk));

        assertThat(book.candidatesFor(taker))
            .extracting(BookOrder::receivedSequence)
            .containsExactly(4L, 2L, 3L);
    }

    @Test
    void detectsCrossingLimitOrdersOnlyWhenPricesOverlap() {
        BookOrder buy = order(MatchingOrderSide.BUY, "100.00", 1);
        BookOrder askAtPrice = order(MatchingOrderSide.SELL, "100.00", 2);
        BookOrder askTooHigh = order(MatchingOrderSide.SELL, "101.00", 3);

        assertThat(buy.crosses(askAtPrice)).isTrue();
        assertThat(buy.crosses(askTooHigh)).isFalse();
    }

    @Test
    void appliesPartialAndFullFillsWithoutNegativeQuantity() {
        BookOrder order = order(MatchingOrderSide.BUY, "100.00", 1);

        order.fill(new BigDecimal("0.4"), Instant.now());
        assertThat(order.status()).isEqualTo(MatchingOrderStatus.PARTIALLY_FILLED);
        assertThat(order.remainingQuantity()).isEqualByComparingTo("0.6");

        order.fill(new BigDecimal("0.6"), Instant.now());
        assertThat(order.status()).isEqualTo(MatchingOrderStatus.FILLED);
        assertThat(order.remainingQuantity()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> order.fill(new BigDecimal("0.1"), Instant.now()))
            .isInstanceOf(MatchingValidationException.class);
    }

    @Test
    void executionIdsAreDeterministicForMarketAndSequence() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();

        Execution first = Execution.create("BTC-USD", buyer, seller, seller, buyer, new BigDecimal("1.0"), new BigDecimal("100.00"), 7, 2, 2, Instant.now());
        Execution second = Execution.create("BTC-USD", buyer, seller, seller, buyer, new BigDecimal("1.0"), new BigDecimal("100.00"), 7, 2, 2, Instant.now());

        assertThat(first.executionId()).isEqualTo(second.executionId());
        assertThat(first.matchId()).isEqualTo(second.matchId());
        assertThat(first.makerOrderId()).isEqualTo(seller);
        assertThat(first.takerOrderId()).isEqualTo(buyer);
    }

    private static BookOrder order(MatchingOrderSide side, String price, long sequence) {
        return BookOrder.accept(
            UUID.randomUUID(),
            "0".repeat(64),
            "BTC-USD",
            side,
            MatchingOrderType.LIMIT,
            new BigDecimal("1.0"),
            new BigDecimal(price),
            sequence,
            Instant.now()
        );
    }
}
