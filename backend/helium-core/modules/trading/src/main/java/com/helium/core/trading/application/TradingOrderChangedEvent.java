package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderStatus;
import java.util.UUID;

public record TradingOrderChangedEvent(
    UUID orderId,
    UUID userId,
    OrderStatus status
) {}
