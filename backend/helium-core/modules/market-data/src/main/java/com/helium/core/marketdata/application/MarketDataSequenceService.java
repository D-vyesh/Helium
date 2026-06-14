package com.helium.core.marketdata.application;

import com.helium.core.marketdata.domain.MarketDataSequence;
import com.helium.core.marketdata.infrastructure.MarketDataSequenceRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
class MarketDataSequenceService {
    private final MarketDataSequenceRepository repository;
    private final Clock clock;

    MarketDataSequenceService(MarketDataSequenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    void apply(String marketSymbol, long sequence) {
        MarketDataSequence marketSequence = repository.findByMarketForUpdate(marketSymbol)
            .orElseGet(() -> MarketDataSequence.start(marketSymbol, clock.instant()));
        marketSequence.apply(sequence, clock.instant());
        repository.saveAndFlush(marketSequence);
    }
}
