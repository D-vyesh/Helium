package com.helium.core.matching.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("matching-engine")
public class MatchingEngineService implements MatchingCommandPort {
    private final SubmitOrderService submitOrderService;
    private final CancelOrderService cancelOrderService;
    private final TrustedTradingActorProvider tradingActorProvider;

    public MatchingEngineService(
        SubmitOrderService submitOrderService,
        CancelOrderService cancelOrderService,
        TrustedTradingActorProvider tradingActorProvider
    ) {
        this.submitOrderService = submitOrderService;
        this.cancelOrderService = cancelOrderService;
        this.tradingActorProvider = tradingActorProvider;
    }

    @Override
    public void submitOrder(SubmitOrderCommand command) {
        tradingActorProvider.requireTradingSystem();
        submitOrderService.submit(command);
    }

    @Override
    public void cancelOrder(CancelOrderCommand command) {
        tradingActorProvider.requireTradingSystem();
        cancelOrderService.cancel(command);
    }

    @Override
    public void expireOrder(ExpireOrderCommand command) {
        tradingActorProvider.requireTradingSystem();
        cancelOrderService.expire(command);
    }
}
