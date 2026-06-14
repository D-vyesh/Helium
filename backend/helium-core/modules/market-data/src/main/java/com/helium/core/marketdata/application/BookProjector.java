package com.helium.core.marketdata.application;

import com.helium.core.marketdata.domain.MarketDataValidationException;
import com.helium.core.marketdata.domain.MarketSymbol;
import com.helium.core.marketdata.domain.OrderBookDelta;
import com.helium.core.marketdata.domain.OrderBookSnapshot;
import com.helium.core.marketdata.infrastructure.OrderBookDeltaRepository;
import com.helium.core.marketdata.infrastructure.OrderBookSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookProjector {
    private final OrderBookSnapshotRepository snapshotRepository;
    private final OrderBookDeltaRepository deltaRepository;
    private final MarketDataSequenceService sequenceService;
    private final MarketDataEventPublisher eventPublisher;
    private final Clock clock;

    public BookProjector(
        OrderBookSnapshotRepository snapshotRepository,
        OrderBookDeltaRepository deltaRepository,
        MarketDataSequenceService sequenceService,
        MarketDataEventPublisher eventPublisher,
        Clock clock
    ) {
        this.snapshotRepository = snapshotRepository;
        this.deltaRepository = deltaRepository;
        this.sequenceService = sequenceService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void projectSnapshot(MarketDataEventPort.BookSnapshotCreated event) {
        String marketSymbol = MarketSymbol.normalize(event.marketSymbol());
        String bidsJson = levelsJson(event.bids());
        String asksJson = levelsJson(event.asks());
        String hash = MarketDataHash.snapshot(marketSymbol, event.sequence(), bidsJson, asksJson);
        var existing = snapshotRepository.findByMarketSymbolAndMarketSequence(marketSymbol, event.sequence());
        if (existing.isPresent()) {
            if (!existing.get().samePayload(hash)) {
                throw new MarketDataValidationException("duplicate book snapshot payload differs");
            }
            return;
        }
        sequenceService.apply(marketSymbol, event.sequence());
        snapshotRepository.save(OrderBookSnapshot.create(marketSymbol, event.sequence(), bidsJson, asksJson, hash, clock.instant()));
        eventPublisher.orderBookUpdated(marketSymbol, event.sequence());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void projectDelta(MarketDataEventPort.BookChanged event) {
        String marketSymbol = MarketSymbol.normalize(event.marketSymbol());
        String side = normalizeSide(event.side());
        String action = normalizeAction(event.action());
        BigDecimal price = event.price().stripTrailingZeros();
        BigDecimal quantity = event.quantity().stripTrailingZeros();
        String hash = MarketDataHash.delta(marketSymbol, event.sequence(), side, price, quantity, action);
        var existing = deltaRepository.findByMarketSymbolAndMarketSequenceAndSideAndPrice(marketSymbol, event.sequence(), side, price);
        if (existing.isPresent()) {
            if (!existing.get().samePayload(hash)) {
                throw new MarketDataValidationException("duplicate book delta payload differs");
            }
            return;
        }
        sequenceService.apply(marketSymbol, event.sequence());
        deltaRepository.save(OrderBookDelta.create(marketSymbol, event.sequence(), side, price, quantity, action, hash, clock.instant()));
        eventPublisher.orderBookUpdated(marketSymbol, event.sequence());
    }

    private String levelsJson(List<MarketDataEventPort.BookLevel> levels) {
        return levels.stream()
            .sorted(Comparator.comparing(MarketDataEventPort.BookLevel::side)
                .thenComparing(MarketDataEventPort.BookLevel::price)
                .thenComparing(MarketDataEventPort.BookLevel::quantity))
            .map(level -> "{\"side\":\"" + normalizeSide(level.side()) + "\",\"price\":\"" + number(level.price()) + "\",\"quantity\":\"" + number(level.quantity()) + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private String normalizeSide(String side) {
        if (!"BID".equalsIgnoreCase(side) && !"ASK".equalsIgnoreCase(side)) {
            throw new MarketDataValidationException("book side is invalid");
        }
        return side.toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeAction(String action) {
        if (!"UPSERT".equalsIgnoreCase(action) && !"DELETE".equalsIgnoreCase(action)) {
            throw new MarketDataValidationException("book action is invalid");
        }
        return action.toUpperCase(java.util.Locale.ROOT);
    }

    private String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
