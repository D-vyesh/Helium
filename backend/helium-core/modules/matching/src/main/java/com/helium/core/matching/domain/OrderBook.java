package com.helium.core.matching.domain;

import java.util.Comparator;
import java.util.List;

public class OrderBook {
    private final String marketSymbol;
    private final List<BookOrder> bids;
    private final List<BookOrder> asks;

    public OrderBook(String marketSymbol, List<BookOrder> bids, List<BookOrder> asks) {
        this.marketSymbol = MatchingText.market(marketSymbol);
        this.bids = bids.stream()
            .filter(BookOrder::matchable)
            .sorted(Comparator.comparing(BookOrder::limitPrice).reversed().thenComparingLong(BookOrder::receivedSequence))
            .toList();
        this.asks = asks.stream()
            .filter(BookOrder::matchable)
            .sorted(Comparator.comparing(BookOrder::limitPrice).thenComparingLong(BookOrder::receivedSequence))
            .toList();
    }

    public List<BookOrder> candidatesFor(BookOrder taker) {
        if (!marketSymbol.equals(taker.marketSymbol())) {
            throw new MatchingValidationException("order market does not match book market");
        }
        return taker.side() == MatchingOrderSide.BUY ? asks : bids;
    }
}
