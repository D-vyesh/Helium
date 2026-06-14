package com.helium.core.marketdata.application;

import com.helium.core.marketdata.domain.MarketDataValidationException;
import com.helium.core.marketdata.domain.MarketSymbol;
import com.helium.core.marketdata.domain.PublicTrade;
import com.helium.core.marketdata.infrastructure.PublicTradeRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionProjector {
    private final PublicTradeRepository tradeRepository;
    private final MarketDataSequenceService sequenceService;
    private final CandleProjector candleProjector;
    private final TickerProjector tickerProjector;
    private final MarketDataEventPublisher eventPublisher;
    private final Clock clock;

    public ExecutionProjector(
        PublicTradeRepository tradeRepository,
        MarketDataSequenceService sequenceService,
        CandleProjector candleProjector,
        TickerProjector tickerProjector,
        MarketDataEventPublisher eventPublisher,
        Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.sequenceService = sequenceService;
        this.candleProjector = candleProjector;
        this.tickerProjector = tickerProjector;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void project(MarketDataEventPort.ExecutionCreated event) {
        String marketSymbol = MarketSymbol.normalize(event.marketSymbol());
        String hash = MarketDataHash.execution(
            event.executionId(),
            event.matchId(),
            marketSymbol,
            event.buyerOrderId(),
            event.sellerOrderId(),
            event.makerOrderId(),
            event.takerOrderId(),
            event.quantity(),
            event.price(),
            event.sequence()
        );
        var existing = tradeRepository.findById(event.executionId());
        if (existing.isPresent()) {
            if (!existing.get().samePayload(hash)) {
                throw new MarketDataValidationException("duplicate execution projection payload differs");
            }
            return;
        }

        sequenceService.apply(marketSymbol, event.sequence());
        PublicTrade trade = PublicTrade.record(
            event.executionId(),
            event.matchId(),
            marketSymbol,
            event.buyerOrderId(),
            event.sellerOrderId(),
            event.makerOrderId(),
            event.takerOrderId(),
            event.price(),
            event.quantity(),
            event.sequence(),
            hash,
            clock.instant()
        );
        tradeRepository.save(trade);
        candleProjector.applyTrade(trade);
        tickerProjector.applyTrade(trade);
        eventPublisher.publicTradeRecorded(marketSymbol, event.executionId());
    }
}
