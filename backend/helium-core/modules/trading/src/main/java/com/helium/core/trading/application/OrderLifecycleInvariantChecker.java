package com.helium.core.trading.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderLifecycleInvariantChecker {
    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleInvariantChecker.class);

    public void verifyOrderQuantities(UUID orderId, BigDecimal initialQuantity, BigDecimal executedQuantity, BigDecimal cancelledQuantity) {
        BigDecimal sum = executedQuantity.add(cancelledQuantity);
        
        if (sum.compareTo(initialQuantity) > 0) {
            log.error("CRITICAL INVARIANT VIOLATION: Order {} over-executed or over-cancelled. Initial: {}, Executed: {}, Cancelled: {}", 
                orderId, initialQuantity, executedQuantity, cancelledQuantity);
            // In a real system, immediately freeze user account and halt trading engine
            throw new IllegalStateException("Order invariant broken: Executed + Cancelled > Initial");
        }
    }
}
