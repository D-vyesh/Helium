package com.helium.core.marketdata.application;

import org.springframework.stereotype.Service;

@Service
public class MarketDataProjectionService implements MarketDataEventPort {
    private final ExecutionProjector executionProjector;
    private final BookProjector bookProjector;
    private final MarketProjector marketProjector;

    public MarketDataProjectionService(
        ExecutionProjector executionProjector,
        BookProjector bookProjector,
        MarketProjector marketProjector
    ) {
        this.executionProjector = executionProjector;
        this.bookProjector = bookProjector;
        this.marketProjector = marketProjector;
    }

    @Override
    public void executionCreated(ExecutionCreated event) {
        executionProjector.project(event);
    }

    @Override
    public void bookChanged(BookChanged event) {
        bookProjector.projectDelta(event);
    }

    @Override
    public void bookSnapshotCreated(BookSnapshotCreated event) {
        bookProjector.projectSnapshot(event);
    }

    @Override
    public void marketCreated(MarketCreated event) {
        marketProjector.projectCreated(event);
    }

    @Override
    public void marketEnabled(MarketEnabled event) {
        marketProjector.projectEnabled(event);
    }

    @Override
    public void marketDisabled(MarketDisabled event) {
        marketProjector.projectDisabled(event);
    }
}
