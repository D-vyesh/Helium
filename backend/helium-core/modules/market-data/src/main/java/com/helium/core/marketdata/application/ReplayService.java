package com.helium.core.marketdata.application;

import com.helium.core.marketdata.infrastructure.CandleRepository;
import com.helium.core.marketdata.infrastructure.MarketDataSequenceRepository;
import com.helium.core.marketdata.infrastructure.OrderBookDeltaRepository;
import com.helium.core.marketdata.infrastructure.OrderBookSnapshotRepository;
import com.helium.core.marketdata.infrastructure.PublicTradeRepository;
import com.helium.core.marketdata.infrastructure.TickerRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayService {
    private final PublicTradeRepository tradeRepository;
    private final TickerRepository tickerRepository;
    private final CandleRepository candleRepository;
    private final OrderBookSnapshotRepository snapshotRepository;
    private final OrderBookDeltaRepository deltaRepository;
    private final MarketDataSequenceRepository sequenceRepository;
    private final MarketDataEventPort projectionService;

    public ReplayService(
        PublicTradeRepository tradeRepository,
        TickerRepository tickerRepository,
        CandleRepository candleRepository,
        OrderBookSnapshotRepository snapshotRepository,
        OrderBookDeltaRepository deltaRepository,
        MarketDataSequenceRepository sequenceRepository,
        MarketDataEventPort projectionService
    ) {
        this.tradeRepository = tradeRepository;
        this.tickerRepository = tickerRepository;
        this.candleRepository = candleRepository;
        this.snapshotRepository = snapshotRepository;
        this.deltaRepository = deltaRepository;
        this.sequenceRepository = sequenceRepository;
        this.projectionService = projectionService;
    }

    @Transactional
    public void clear() {
        deltaRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        candleRepository.deleteAllInBatch();
        tickerRepository.deleteAllInBatch();
        tradeRepository.deleteAllInBatch();
        sequenceRepository.deleteAllInBatch();
    }

    public void replayExecutions(List<MarketDataEventPort.ExecutionCreated> executions) {
        clear();
        executions.stream()
            .sorted(Comparator.comparingLong(MarketDataEventPort.ExecutionCreated::sequence))
            .forEach(projectionService::executionCreated);
    }

    public void replaySnapshots(List<MarketDataEventPort.BookSnapshotCreated> snapshots) {
        clear();
        snapshots.stream()
            .sorted(Comparator.comparingLong(MarketDataEventPort.BookSnapshotCreated::sequence))
            .forEach(projectionService::bookSnapshotCreated);
    }
}
