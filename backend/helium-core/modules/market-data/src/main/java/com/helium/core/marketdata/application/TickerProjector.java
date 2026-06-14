package com.helium.core.marketdata.application;

import com.helium.core.marketdata.domain.MarketSymbol;
import com.helium.core.marketdata.domain.PublicTrade;
import com.helium.core.marketdata.domain.Ticker;
import com.helium.core.marketdata.infrastructure.PublicTradeRepository;
import com.helium.core.marketdata.infrastructure.TickerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TickerProjector {
    private final TickerRepository tickerRepository;
    private final PublicTradeRepository tradeRepository;
    private final MarketDataEventPublisher eventPublisher;
    private final Clock clock;

    public TickerProjector(
        TickerRepository tickerRepository,
        PublicTradeRepository tradeRepository,
        MarketDataEventPublisher eventPublisher,
        Clock clock
    ) {
        this.tickerRepository = tickerRepository;
        this.tradeRepository = tradeRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    void applyTrade(PublicTrade trade) {
        String marketSymbol = MarketSymbol.normalize(trade.marketSymbol());
        Instant now = clock.instant();
        List<PublicTrade> window = tradeRepository.findByMarketSymbolAndTradedAtGreaterThanEqualOrderByTradedAtAscMarketSequenceAsc(
            marketSymbol,
            now.minusSeconds(86_400)
        );
        if (window.isEmpty()) {
            window = List.of(trade);
        }
        BigDecimal open = window.getFirst().price();
        BigDecimal last = window.getLast().price();
        BigDecimal high = window.stream().map(PublicTrade::price).max(Comparator.naturalOrder()).orElse(last);
        BigDecimal low = window.stream().map(PublicTrade::price).min(Comparator.naturalOrder()).orElse(last);
        BigDecimal volume = window.stream().map(PublicTrade::quantity).reduce(BigDecimal.ZERO, BigDecimal::add).stripTrailingZeros();
        BigDecimal quoteVolume = window.stream()
            .map(item -> item.price().multiply(item.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .stripTrailingZeros();

        Ticker ticker = tickerRepository.findById(marketSymbol).orElseGet(() -> Ticker.create(marketSymbol, now));
        ticker.updateWindow(last, open, high, low, volume, quoteVolume, window.size(), now);
        tickerRepository.save(ticker);
        eventPublisher.tickerUpdated(marketSymbol);
    }

    public void setMarketEnabled(String marketSymbol, boolean enabled) {
        String normalized = MarketSymbol.normalize(marketSymbol);
        Ticker ticker = tickerRepository.findById(normalized).orElseGet(() -> Ticker.create(normalized, clock.instant()));
        ticker.setEnabled(enabled, clock.instant());
        tickerRepository.save(ticker);
        eventPublisher.tickerUpdated(normalized);
        eventPublisher.marketsUpdated();
    }
}
