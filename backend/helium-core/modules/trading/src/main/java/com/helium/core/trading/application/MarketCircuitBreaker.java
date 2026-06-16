package com.helium.core.trading.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketCircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(MarketCircuitBreaker.class);

    // Track the reference price (e.g. 5-minute VWAP) per market
    private final Map<String, BigDecimal> referencePrices = new ConcurrentHashMap<>();
    
    // Configurable deviation percentage before triggering a halt (e.g. 0.10 for 10%)
    private static final BigDecimal HALT_THRESHOLD = new BigDecimal("0.10");

    public void recordPrice(String marketId, BigDecimal currentPrice) {
        BigDecimal referencePrice = referencePrices.getOrDefault(marketId, currentPrice);
        
        // Calculate absolute deviation
        BigDecimal deviation = currentPrice.subtract(referencePrice).abs()
                .divide(referencePrice, 4, java.math.RoundingMode.HALF_UP);

        if (deviation.compareTo(HALT_THRESHOLD) > 0) {
            log.error("CIRCUIT BREAKER TRIGGERED: Market {} deviated by {}%. Halting matching engine.", 
                marketId, deviation.multiply(new BigDecimal("100")));
            triggerVolatilityHalt(marketId);
        } else {
            // Update reference price smoothly
            referencePrices.put(marketId, currentPrice);
        }
    }

    private void triggerVolatilityHalt(String marketId) {
        // In a real system, this interacts with the MatchingEngine to pause order matching
        // and transitions the market into an Auction phase for price discovery.
        log.warn("Market {} is now HALTED. Transitioning to Auction mode.", marketId);
    }
}
