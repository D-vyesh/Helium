package com.helium.core.matching.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface MatchingCommandPort {
    void submitOrder(SubmitOrderCommand command);
    void cancelOrder(CancelOrderCommand command);
    void expireOrder(ExpireOrderCommand command);
    
    void haltMarket(HaltMarketCommand command);
    void startAuction(StartAuctionCommand command);
    void uncrossAndResume(ResumeMarketCommand command);

    record SubmitOrderCommand(
        UUID orderId,
        String marketSymbol,
        String side,
        String orderType,
        String timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice   // null for LIMIT / MARKET / POST_ONLY
    ) {}

    record CancelOrderCommand(
        UUID orderId,
        String marketSymbol
    ) {}

    record ExpireOrderCommand(
        UUID orderId,
        String marketSymbol
    ) {}

    record HaltMarketCommand(
        String marketSymbol
    ) {}

    record StartAuctionCommand(
        String marketSymbol
    ) {}

    record ResumeMarketCommand(
        String marketSymbol
    ) {}
}
