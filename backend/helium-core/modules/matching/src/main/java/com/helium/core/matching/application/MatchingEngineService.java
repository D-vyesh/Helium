package com.helium.core.matching.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!no-matching")
public class MatchingEngineService implements MatchingCommandPort {
    private final SubmitOrderService submitOrderService;
    private final CancelOrderService cancelOrderService;
    private final MarketStateControlService marketStateControlService;
    private final TrustedTradingActorProvider tradingActorProvider;

    public MatchingEngineService(
        SubmitOrderService submitOrderService,
        CancelOrderService cancelOrderService,
        MarketStateControlService marketStateControlService,
        TrustedTradingActorProvider tradingActorProvider
    ) {
        this.submitOrderService = submitOrderService;
        this.cancelOrderService = cancelOrderService;
        this.marketStateControlService = marketStateControlService;
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

    @Override
    public void haltMarket(HaltMarketCommand command) {
        tradingActorProvider.requireTradingSystem();
        marketStateControlService.haltMarket(command);
    }

    @Override
    public void startAuction(StartAuctionCommand command) {
        tradingActorProvider.requireTradingSystem();
        marketStateControlService.startAuction(command);
    }

    @Override
    public void uncrossAndResume(ResumeMarketCommand command) {
        tradingActorProvider.requireTradingSystem();
        marketStateControlService.uncrossAndResume(command);
    }
}
