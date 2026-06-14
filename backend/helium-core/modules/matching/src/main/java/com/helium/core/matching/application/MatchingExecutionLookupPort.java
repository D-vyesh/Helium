package com.helium.core.matching.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface MatchingExecutionLookupPort {
    Optional<MatchingExecutionView> findByExecutionId(String executionId);

    record MatchingExecutionView(
        String executionId,
        String matchId,
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal quantity,
        BigDecimal price,
        long sequence,
        long buyerOrderOffset,
        long sellerOrderOffset
    ) {
    }
}
