package com.helium.core.matching.application;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.MatchingOrderSide;
import com.helium.core.matching.domain.MatchingText;
import com.helium.core.matching.infrastructure.BookOrderRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderBookQueryService implements OrderBookQueryPort {
    private final BookOrderRepository repository;

    public OrderBookQueryService(BookOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrderBookView getOrderBook(String marketSymbol) {
        String normalizedMarket = MatchingText.market(marketSymbol);
        List<BookOrder> orders = repository.findOpenByMarket(normalizedMarket);
        List<BookOrderView> bids = orders.stream()
            .filter(order -> order.side() == MatchingOrderSide.BUY)
            .sorted(Comparator.comparing(BookOrder::limitPrice).reversed().thenComparingLong(BookOrder::receivedSequence))
            .map(this::view)
            .toList();
        List<BookOrderView> asks = orders.stream()
            .filter(order -> order.side() == MatchingOrderSide.SELL)
            .sorted(Comparator.comparing(BookOrder::limitPrice).thenComparingLong(BookOrder::receivedSequence))
            .map(this::view)
            .toList();
        return new OrderBookView(normalizedMarket, bids, asks);
    }

    private BookOrderView view(BookOrder order) {
        return new BookOrderView(order.orderId(), order.limitPrice(), order.remainingQuantity(), order.receivedSequence());
    }
}
