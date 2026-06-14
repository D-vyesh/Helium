package com.helium.core.trading.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface TradingSettlementPort {
    void processExecution(TradeExecutionCommand command);

    record TradeExecutionCommand(
        String executionId,
        long marketSequence,
        long buyerOrderOffset,
        long sellerOrderOffset,
        UUID buyerOrderId,
        UUID sellerOrderId,
        String marketSymbol,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal buyerFee,
        BigDecimal sellerFee
    ) {}
}
