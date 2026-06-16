package com.helium.core.trading.application;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskSurveillanceEngine {
    private static final Logger log = LoggerFactory.getLogger(RiskSurveillanceEngine.class);

    // Track order creation timestamps for Spoofing detection
    private final Map<UUID, Long> orderCreationTimes = new ConcurrentHashMap<>();

    public void recordOrderCreation(UUID orderId) {
        orderCreationTimes.put(orderId, System.currentTimeMillis());
    }

    public void analyzeExecutionForWashTrading(UUID makerId, UUID takerId, String marketId, String tradeId) {
        if (makerId.equals(takerId)) {
            log.error("RISK ALERT: Wash Trading Detected! User {} matched against their own order in market {}. Trade ID: {}", 
                makerId, marketId, tradeId);
            // In a real system, we would:
            // 1. Emit an event to suspend the account
            // 2. Alert the COMPLIANCE_OFFICER
            // 3. Mark the trade as suspicious in the DB
        }
    }

    public void analyzeCancellationForSpoofing(UUID orderId, UUID userId, String marketId) {
        Long creationTime = orderCreationTimes.remove(orderId);
        if (creationTime != null) {
            long lifetime = System.currentTimeMillis() - creationTime;
            // If order was cancelled in under 50ms without execution, flag as potential spoofing
            if (lifetime < 50) {
                log.warn("RISK ALERT: Potential Spoofing Detected. Order {} in market {} by user {} cancelled after {}ms", 
                    orderId, marketId, userId, lifetime);
                // In a real system: increment spoofing counter for user, flag if pattern persists
            }
        }
    }

    public void analyzeForLayering(UUID userId, String marketId, int concurrentOrdersOnSameSide) {
        if (concurrentOrdersOnSameSide > 5) {
            log.warn("RISK ALERT: Potential Layering. User {} placed {} orders on the same side in market {}", 
                userId, concurrentOrdersOnSameSide, marketId);
            // In a real system: monitor for subsequent cancellations of these layered orders
        }
    }

    public void detectInsiderTrading(UUID userId, String assetCode, java.math.BigDecimal volumeUsd) {
        // Mock detection: flags large volume anomalies before a new listing
        if (volumeUsd.compareTo(new java.math.BigDecimal("1000000.00")) > 0) {
            log.error("SURVEILLANCE ALERT: Unusual high volume ({} USD) detected for user {} on asset {}. Flagging for insider trading review.", 
                volumeUsd, userId, assetCode);
        }
    }
}
