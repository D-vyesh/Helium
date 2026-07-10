package com.helium.core.app.api;

import com.helium.core.trading.application.TradingOrderChangedEvent;
import com.helium.core.trading.domain.OrderStatus;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TradingNotificationEventPublisher {
    private final NotificationService notificationService;

    public TradingNotificationEventPublisher(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void orderChangedAfterCommit(TradingOrderChangedEvent event) {
        NotificationTemplate template = template(event.status());
        if (template == null) {
            return;
        }
        notificationService.create(
            event.userId(),
            "TRADING",
            template.eventType(),
            template.title(),
            template.message(),
            Map.of(
                "orderId", event.orderId().toString(),
                "status", event.status().name()
            )
        );
    }

    private NotificationTemplate template(OrderStatus status) {
        return switch (status) {
            case OPEN -> new NotificationTemplate("ORDER_ACCEPTED", "Order accepted", "Your order is now live on the order book.");
            case PARTIALLY_FILLED -> new NotificationTemplate("ORDER_PARTIALLY_FILLED", "Order partially filled", "Your order received a partial execution.");
            case FILLED -> new NotificationTemplate("ORDER_FILLED", "Order filled", "Your order has been fully executed.");
            case CANCELLED -> new NotificationTemplate("ORDER_CANCELLED", "Order cancelled", "Your order cancellation is complete.");
            case REJECTED -> new NotificationTemplate("ORDER_REJECTED", "Order rejected", "Your order was rejected by the trading workflow.");
            default -> null;
        };
    }

    private record NotificationTemplate(String eventType, String title, String message) {}
}
