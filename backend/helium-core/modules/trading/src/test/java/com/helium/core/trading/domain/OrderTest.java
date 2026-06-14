package com.helium.core.trading.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void testOrderLifecycle() {
        Market market = Market.register("BTC-USD", "BTC", "USD", 2, 8, new BigDecimal("0.001"), new BigDecimal("10.0"), true, Instant.now());
        
        Order order = order();

        assertEquals(OrderStatus.RECEIVED, order.status());

        order.validateAgainst(market, Instant.now());
        assertEquals(OrderStatus.VALIDATED, order.status());

        order.markFundsReserved(Instant.now());
        assertEquals(OrderStatus.FUNDS_RESERVED, order.status());

        order.markSentToMatching(Instant.now());
        assertEquals(OrderStatus.SENT_TO_MATCHING, order.status());

        order.accept(1, Instant.now());
        assertEquals(OrderStatus.OPEN, order.status());

        order.applyFill(new BigDecimal("0.4"), 2, Instant.now());
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.status());
        assertEquals(new BigDecimal("0.4"), order.filledQuantity());
        assertEquals(new BigDecimal("0.6"), order.remainingQuantity());

        order.applyFill(new BigDecimal("0.6"), 3, Instant.now());
        assertEquals(OrderStatus.FILLED, order.status());
        assertEquals(new BigDecimal("1"), order.filledQuantity());
        assertTrue(order.terminal());
    }

    @Test
    void testTerminalStateProtection() {
        Order order = order();

        order.reject("insufficient funds", 1, Instant.now());
        assertEquals(OrderStatus.REJECTED, order.status());
        assertTrue(order.terminal());

        assertThrows(TradingValidationException.class, () -> order.markFundsReserved(Instant.now()));
    }

    @Test
    void cancelRequestedStillAcceptsExecutionUntilCancelConfirmation() {
        Market market = Market.register("BTC-USD", "BTC", "USD", 2, 8, new BigDecimal("0.001"), new BigDecimal("10.0"), true, Instant.now());
        Order order = order("client-cancel", "hash-cancel");
        order.validateAgainst(market, Instant.now());
        order.markFundsReserved(Instant.now());
        order.markSentToMatching(Instant.now());
        order.accept(1, Instant.now());

        order.startCancellation(Instant.now());
        order.applyFill(new BigDecimal("0.4"), 2, Instant.now());

        assertEquals(OrderStatus.CANCEL_REQUESTED, order.status());
        assertEquals(new BigDecimal("0.4"), order.filledQuantity());
        assertEquals(new BigDecimal("0.6"), order.remainingQuantity());
    }

    @Test
    void rejectsOutOfOrderMatchingEvents() {
        Market market = Market.register("BTC-USD", "BTC", "USD", 2, 8, new BigDecimal("0.001"), new BigDecimal("10.0"), true, Instant.now());
        Order order = order("client-sequence", "hash-sequence");
        order.validateAgainst(market, Instant.now());
        order.markFundsReserved(Instant.now());
        order.markSentToMatching(Instant.now());

        assertThrows(TradingValidationException.class, () -> order.accept(2, Instant.now()));
    }

    private static Order order() {
        return order("client1", "hash1");
    }

    private static Order order(String clientOrderId, String requestHash) {
        return Order.receive(
            UUID.randomUUID(),
            clientOrderId,
            requestHash,
            "BTC-USD",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            new BigDecimal("1.0"),
            new BigDecimal("50000.0"),
            new BigDecimal("0.0010"),
            "USD",
            "test-policy",
            Instant.now()
        );
    }
}
