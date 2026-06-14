package com.helium.core.marketdata.application;

import com.helium.core.matching.application.MatchingEventPort;
import com.helium.core.matching.application.OrderBookQueryPort;
import com.helium.core.marketdata.domain.MarketDataValidationException;
import com.helium.core.marketdata.domain.MarketSymbol;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class MatchingMarketDataEventAdapter implements MatchingEventPort {
    private final MarketDataEventPort marketDataEventPort;
    private final OrderBookQueryPort orderBookQueryPort;
    private final WebSocketBroadcaster broadcaster;

    MatchingMarketDataEventAdapter(
        MarketDataEventPort marketDataEventPort,
        OrderBookQueryPort orderBookQueryPort,
        WebSocketBroadcaster broadcaster
    ) {
        this.marketDataEventPort = marketDataEventPort;
        this.orderBookQueryPort = orderBookQueryPort;
        this.broadcaster = broadcaster;
    }

    @Override
    public void orderAccepted(OrderAcceptedEvent event) {
        projectSnapshot(event.marketSymbol(), event.marketSequence());
    }

    @Override
    public void orderCancelled(OrderCancelledEvent event) {
        projectSnapshot(event.marketSymbol(), event.marketSequence());
    }

    @Override
    public void orderExpired(OrderExpiredEvent event) {
        projectSnapshot(event.marketSymbol(), event.marketSequence());
    }

    @Override
    public void executionCreated(ExecutionCreatedEvent event) {
        try {
            marketDataEventPort.executionCreated(new MarketDataEventPort.ExecutionCreated(
                event.executionId(),
                event.matchId(),
                event.marketSymbol(),
                event.buyerOrderId(),
                event.sellerOrderId(),
                event.makerOrderId(),
                event.takerOrderId(),
                event.quantity(),
                event.price(),
                event.sequence()
            ));
        } catch (MarketDataValidationException exception) {
            signalResync(event.marketSymbol());
        }
    }

    private void projectSnapshot(String marketSymbol, long sequence) {
        OrderBookQueryPort.OrderBookView book = orderBookQueryPort.getOrderBook(marketSymbol);
        List<MarketDataEventPort.BookLevel> bids = book.bids().stream()
            .map(level -> new MarketDataEventPort.BookLevel("BID", level.price(), level.remainingQuantity()))
            .toList();
        List<MarketDataEventPort.BookLevel> asks = book.asks().stream()
            .map(level -> new MarketDataEventPort.BookLevel("ASK", level.price(), level.remainingQuantity()))
            .toList();
        try {
            marketDataEventPort.bookSnapshotCreated(new MarketDataEventPort.BookSnapshotCreated(marketSymbol, sequence, bids, asks));
        } catch (MarketDataValidationException exception) {
            signalResync(marketSymbol);
        }
    }

    private void signalResync(String marketSymbol) {
        broadcaster.broadcast("/book." + MarketSymbol.normalize(marketSymbol) + ".snapshot", "RESYNC_REQUIRED");
    }
}
