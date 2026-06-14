package com.helium.core.marketdata.application;

import com.helium.core.marketdata.domain.Candle;
import com.helium.core.marketdata.domain.PublicTrade;
import com.helium.core.marketdata.infrastructure.CandleRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class CandleProjector {
    static final String ONE_MINUTE = "1m";
    private static final Duration ONE_MINUTE_DURATION = Duration.ofMinutes(1);

    private final CandleRepository repository;
    private final MarketDataEventPublisher eventPublisher;

    public CandleProjector(CandleRepository repository, MarketDataEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    void applyTrade(PublicTrade trade) {
        Instant openTime = trade.tradedAt().truncatedTo(ChronoUnit.MINUTES);
        Instant closeTime = openTime.plus(ONE_MINUTE_DURATION);
        repository.findByMarketSymbolAndIntervalNameAndOpenTime(trade.marketSymbol(), ONE_MINUTE, openTime)
            .ifPresentOrElse(
                candle -> {
                    candle.addTrade(trade.price(), trade.quantity());
                    repository.save(candle);
                },
                () -> repository.save(Candle.open(trade.marketSymbol(), ONE_MINUTE, openTime, closeTime, trade.price(), trade.quantity()))
            );
    }

    public void closeCompletedCandles(String marketSymbol, Instant before) {
        repository.findAll().stream()
            .filter(candle -> candle.marketSymbol().equals(marketSymbol))
            .filter(candle -> !candle.closed())
            .filter(candle -> candle.closeTime().isBefore(before) || candle.closeTime().equals(before))
            .forEach(candle -> {
                candle.close();
                repository.save(candle);
                eventPublisher.candleClosed(marketSymbol, ONE_MINUTE);
            });
    }
}
