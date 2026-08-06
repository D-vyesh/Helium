package com.helium.core.trading.infrastructure;

import com.helium.core.matching.application.MatchingCommandPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("no-matching")
public class NoOpMatchingCommandPortAdapter implements MatchingCommandPort {
    @Override
    public void submitOrder(SubmitOrderCommand command) {
        // No-op
    }

    @Override
    public void cancelOrder(CancelOrderCommand command) {
        // No-op
    }

    @Override
    public void expireOrder(ExpireOrderCommand command) {
        // No-op
    }

    @Override
    public void haltMarket(HaltMarketCommand command) {
        // No-op
    }

    @Override
    public void startAuction(StartAuctionCommand command) {
        // No-op
    }

    @Override
    public void uncrossAndResume(ResumeMarketCommand command) {
        // No-op
    }
}
