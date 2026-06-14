package com.helium.core.matching.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrderBookQueryPort {
    OrderBookView getOrderBook(String marketSymbol);

    record OrderBookView(String marketSymbol, List<BookOrderView> bids, List<BookOrderView> asks) {
    }

    record BookOrderView(UUID orderId, BigDecimal price, BigDecimal remainingQuantity, long receivedSequence) {
    }
}
