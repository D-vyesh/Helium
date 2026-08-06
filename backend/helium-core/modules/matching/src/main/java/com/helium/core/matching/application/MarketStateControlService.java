package com.helium.core.matching.application;

import com.helium.core.matching.domain.MarketMatchingState;
import com.helium.core.matching.domain.MatchingValidationException;
import com.helium.core.matching.infrastructure.MarketMatchingStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class MarketStateControlService {

    private final MarketMatchingStateRepository stateRepository;
    private final SubmitOrderService submitOrderService;
    private final Clock clock;

    public MarketStateControlService(MarketMatchingStateRepository stateRepository, SubmitOrderService submitOrderService, Clock clock) {
        this.stateRepository = stateRepository;
        this.submitOrderService = submitOrderService;
        this.clock = clock;
    }

    @Transactional
    public void haltMarket(MatchingCommandPort.HaltMarketCommand command) {
        MarketMatchingState state = stateRepository.findById(command.marketSymbol())
            .orElseGet(() -> MarketMatchingState.start(command.marketSymbol(), clock.instant()));
        
        state.halt(clock.instant());
        stateRepository.save(state);
    }

    @Transactional
    public void startAuction(MatchingCommandPort.StartAuctionCommand command) {
        MarketMatchingState state = stateRepository.findById(command.marketSymbol())
            .orElseThrow(() -> new MatchingValidationException("Market state not found"));
        
        state.startAuction(clock.instant());
        stateRepository.save(state);
    }

    @Transactional
    public void uncrossAndResume(MatchingCommandPort.ResumeMarketCommand command) {
        MarketMatchingState state = stateRepository.findById(command.marketSymbol())
            .orElseThrow(() -> new MatchingValidationException("Market state not found"));
        
        // Execute the uncrossing algorithm to match resting overlapping orders
        submitOrderService.uncross(command.marketSymbol());
        
        state.resume(clock.instant());
        stateRepository.save(state);
    }
}
