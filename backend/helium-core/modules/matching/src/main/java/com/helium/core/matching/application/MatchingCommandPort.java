package com.helium.core.matching.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface MatchingCommandPort {
    void submitOrder(SubmitOrderCommand command);
    void cancelOrder(CancelOrderCommand command);
    void expireOrder(ExpireOrderCommand command);

    record SubmitOrderCommand(
        UUID orderId,
        String marketSymbol,
        String side,
        String orderType,
        String timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice
    ) {}

    record CancelOrderCommand(
        UUID orderId,
        String marketSymbol
    ) {}

    record ExpireOrderCommand(
        UUID orderId,
        String marketSymbol
    ) {}
}
