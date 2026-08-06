package com.helium.core.matching.application;

import com.helium.core.matching.domain.MarketMatchingState;
import com.helium.core.matching.domain.MarketMatchingStatus;
import com.helium.core.matching.infrastructure.MarketMatchingStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that monitors halted and auctioning markets, advancing
 * their state to auction and then resuming them once the configured
 * durations have passed.
 */
@Service
public class MarketHaltScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketHaltScheduler.class);

    private final MarketMatchingStateRepository stateRepository;
    private final MarketStateControlService stateControlService;
    private final Clock clock;
    
    private final int haltDurationMinutes;
    private final int auctionDurationMinutes;

    public MarketHaltScheduler(
        MarketMatchingStateRepository stateRepository,
        MarketStateControlService stateControlService,
        Clock clock,
        @Value("${helium.matching.circuit-breaker.halt-duration-minutes:5}") int haltDurationMinutes,
        @Value("${helium.matching.circuit-breaker.auction-duration-minutes:5}") int auctionDurationMinutes
    ) {
        this.stateRepository = stateRepository;
        this.stateControlService = stateControlService;
        this.clock = clock;
        this.haltDurationMinutes = haltDurationMinutes;
        this.auctionDurationMinutes = auctionDurationMinutes;
    }

    @Scheduled(fixedDelay = 60000) // runs every minute
    public void processHaltedMarkets() {
        List<MarketMatchingState> abnormalStates = stateRepository.findByStatusIn(
            List.of(MarketMatchingStatus.HALTED, MarketMatchingStatus.AUCTION)
        );

        Instant now = clock.instant();

        for (MarketMatchingState state : abnormalStates) {
            long minutesSinceUpdate = ChronoUnit.MINUTES.between(state.updatedAt(), now);

            if (state.status() == MarketMatchingStatus.HALTED && minutesSinceUpdate >= haltDurationMinutes) {
                log.info("Market {} has been HALTED for {} minutes (threshold: {}). Transitioning to AUCTION.",
                    state.marketSymbol(), minutesSinceUpdate, haltDurationMinutes);
                try {
                    stateControlService.startAuction(new MatchingCommandPort.StartAuctionCommand(state.marketSymbol()));
                } catch (Exception ex) {
                    log.error("Failed to transition market {} to AUCTION", state.marketSymbol(), ex);
                }
            } else if (state.status() == MarketMatchingStatus.AUCTION && minutesSinceUpdate >= auctionDurationMinutes) {
                log.info("Market {} has been in AUCTION for {} minutes (threshold: {}). Uncrossing and RESUMING.",
                    state.marketSymbol(), minutesSinceUpdate, auctionDurationMinutes);
                try {
                    stateControlService.uncrossAndResume(new MatchingCommandPort.ResumeMarketCommand(state.marketSymbol()));
                } catch (Exception ex) {
                    log.error("Failed to uncross and resume market {}", state.marketSymbol(), ex);
                }
            }
        }
    }
}
