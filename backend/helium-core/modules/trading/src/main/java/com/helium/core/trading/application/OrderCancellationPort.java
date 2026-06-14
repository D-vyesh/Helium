package com.helium.core.trading.application;

import java.util.UUID;

public interface OrderCancellationPort {
    void cancelOrder(CancelOrderCommand command);

    record CancelOrderCommand(
        UUID orderId
    ) {}
}
