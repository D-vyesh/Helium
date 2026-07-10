package com.helium.core.app.api;

import com.helium.core.trading.application.TradingOrderChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TradingRealtimeEventPublisher {
    private final TradingWebSocketHandler webSocketHandler;

    public TradingRealtimeEventPublisher(TradingWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void orderChangedAfterCommit(TradingOrderChangedEvent event) {
        webSocketHandler.broadcast(event.userId(), "order", new OrderChangedPayload(event.orderId(), event.status().name()));
    }

    private record OrderChangedPayload(Object orderId, String status) {}
}
