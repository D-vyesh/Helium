package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TimeInForce;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderQueryPort {
    Optional<OrderView> getOrder(UUID orderId);
    List<OrderView> getOrders();

    record OrderView(
        UUID id,
        UUID userId,
        String clientOrderId,
        String marketSymbol,
        OrderSide side,
        OrderType orderType,
        OrderStatus status,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal filledQuantity,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
