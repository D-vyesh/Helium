package com.helium.core.matching.domain;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory representation of one side of the order book for a single market.
 *
 * <h2>Matching Priority Algorithm: Price-Time (FIFO)</h2>
 *
 * <p>This order book sorts resting orders using <b>strict price-time priority</b>:
 * <ul>
 *   <li><b>Bids</b> (buy side): highest price first, then earliest {@code receivedSequence}.</li>
 *   <li><b>Asks</b> (sell side): lowest price first, then earliest {@code receivedSequence}.</li>
 * </ul>
 *
 * <p>This is the same algorithm used by Binance, Coinbase, NYSE, and NASDAQ for
 * equities and spot crypto. It rewards speed and aggressive pricing, which leads
 * to tighter spreads and better price discovery for retail participants.
 *
 * <h3>Why not pro-rata?</h3>
 *
 * <p>Pro-rata allocation (used by CME for some derivatives like Eurodollar futures)
 * distributes fills proportionally across all resting orders at the same price level.
 * It incentivises <em>size</em> over speed, which is desirable in markets where many
 * participants quote identical prices (e.g., interest-rate derivatives).
 *
 * <p>For a crypto spot exchange, pro-rata has significant drawbacks:
 * <ul>
 *   <li>Encourages quote stuffing (posting large resting orders to capture more fill share).</li>
 *   <li>Adds computational complexity with minimal benefit in markets with wide tick sizes.</li>
 *   <li>Breaks the expectation of retail traders who assume FIFO ordering.</li>
 * </ul>
 *
 * <h3>Extensibility</h3>
 *
 * <p>If pro-rata is needed in the future (e.g., for a derivatives module), a
 * {@code MatchingStrategy} interface can be introduced. The {@code OrderBook} would
 * delegate sorting and fill allocation to the strategy, parameterised per-market.
 * This would require:
 * <ol>
 *   <li>A {@code matching_priority} column on the market configuration (e.g., {@code PRICE_TIME} or {@code PRO_RATA}).</li>
 *   <li>A {@code ProRataOrderBook} implementation that groups orders by price level and allocates fills proportionally.</li>
 *   <li>Minimum allocation thresholds to avoid dust fills.</li>
 * </ol>
 */
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

