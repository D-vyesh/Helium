package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TimeInForce;
import java.math.BigDecimal;
import java.util.UUID;

public interface OrderPlacementPort {
    UUID placeOrder(PlaceOrderCommand command);

    record PlaceOrderCommand(
        String clientOrderId,
        String marketSymbol,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice
    ) {}
}
