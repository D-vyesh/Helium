package com.helium.core.trading.application;

import com.helium.core.outbox.application.OutboxPublisher;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.infrastructure.MarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketCircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(MarketCircuitBreaker.class);

    // Track the reference price (e.g. 5-minute VWAP) per market
    private final Map<String, BigDecimal> referencePrices = new ConcurrentHashMap<>();
    
    // Configurable deviation percentage before triggering a halt (e.g. 0.10 for 10%)
    private static final BigDecimal HALT_THRESHOLD = new BigDecimal("0.10");

    private final MarketRepository marketRepository;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public MarketCircuitBreaker(MarketRepository marketRepository, OutboxPublisher outboxPublisher, Clock clock) {
        this.marketRepository = marketRepository;
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    @Transactional
    public void recordPrice(String marketId, BigDecimal currentPrice) {
        Market market = marketRepository.findById(marketId).orElse(null);
        if (market == null) return;

        BigDecimal referencePrice = referencePrices.get(marketId);
        if (referencePrice == null) {
            referencePrice = market.lastReferencePrice() != null ? market.lastReferencePrice() : currentPrice;
            referencePrices.put(marketId, referencePrice);
        }
        
        // Calculate absolute deviation
        BigDecimal deviation = currentPrice.subtract(referencePrice).abs()
                .divide(referencePrice, 4, java.math.RoundingMode.HALF_UP);

        if (deviation.compareTo(HALT_THRESHOLD) > 0) {
            log.error("CIRCUIT BREAKER TRIGGERED: Market {} deviated by {}%. Halting matching engine.", 
                marketId, deviation.multiply(new BigDecimal("100")));
            triggerVolatilityHalt(marketId);
        } else {
            // Update reference price smoothly if deviation > 1% to avoid excessive DB writes
            if (deviation.compareTo(new BigDecimal("0.01")) > 0) {
                referencePrices.put(marketId, currentPrice);
                market.updateReferencePrice(currentPrice, clock.instant());
                marketRepository.save(market);
            }
        }
    }

    private void triggerVolatilityHalt(String marketId) {
        log.warn("Market {} is now HALTED. Transitioning to Auction mode.", marketId);
        outboxPublisher.publish("MARKET", marketId, "TRADING.MARKET_HALTED", "{\"marketSymbol\":\"" + marketId + "\"}");
    }
}
