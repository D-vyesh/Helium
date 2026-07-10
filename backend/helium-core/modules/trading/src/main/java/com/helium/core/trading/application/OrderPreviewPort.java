package com.helium.core.trading.application;

import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TimeInForce;
import java.math.BigDecimal;
import java.util.List;

public interface OrderPreviewPort {
    OrderPreview preview(PreviewOrderCommand command);

    record PreviewOrderCommand(
        String marketSymbol,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice
    ) {}

    record OrderPreview(
        String marketSymbol,
        String baseAsset,
        String quoteAsset,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal notional,
        BigDecimal estimatedFee,
        String feeAsset,
        BigDecimal feeRate,
        String reserveAsset,
        BigDecimal reserveAmount,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        int priceScale,
        int quantityScale,
        List<OrderType> supportedOrderTypes
    ) {}
}
