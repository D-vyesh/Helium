package com.helium.core.marketdata.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MarketDataEventPort {
    void executionCreated(ExecutionCreated event);

    void bookChanged(BookChanged event);

    void bookSnapshotCreated(BookSnapshotCreated event);

    void marketCreated(MarketCreated event);

    void marketEnabled(MarketEnabled event);

    void marketDisabled(MarketDisabled event);

    record ExecutionCreated(
        String executionId,
        String matchId,
        String marketSymbol,
        UUID buyerOrderId,
        UUID sellerOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        BigDecimal quantity,
        BigDecimal price,
        long sequence
    ) {
    }

    record BookLevel(String side, BigDecimal price, BigDecimal quantity) {
    }

    record BookChanged(String marketSymbol, long sequence, String side, BigDecimal price, BigDecimal quantity, String action) {
    }

    record BookSnapshotCreated(String marketSymbol, long sequence, List<BookLevel> bids, List<BookLevel> asks) {
    }

    record MarketCreated(String marketSymbol, boolean enabled) {
    }

    record MarketEnabled(String marketSymbol) {
    }

    record MarketDisabled(String marketSymbol) {
    }
}
