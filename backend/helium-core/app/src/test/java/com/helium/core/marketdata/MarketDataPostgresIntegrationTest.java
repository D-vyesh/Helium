package com.helium.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.marketdata.application.BookProjector;
import com.helium.core.marketdata.application.CandleProjector;
import com.helium.core.marketdata.application.MarketDataEventPort;
import com.helium.core.marketdata.application.RecordingWebSocketBroadcaster;
import com.helium.core.marketdata.application.ReplayService;
import com.helium.core.marketdata.domain.MarketDataValidationException;
import com.helium.core.marketdata.infrastructure.CandleRepository;
import com.helium.core.marketdata.infrastructure.MarketDataSequenceRepository;
import com.helium.core.marketdata.infrastructure.OrderBookSnapshotRepository;
import com.helium.core.marketdata.infrastructure.PublicTradeRepository;
import com.helium.core.marketdata.infrastructure.TickerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class)
@Testcontainers
class MarketDataPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MarketDataEventPort marketDataEventPort;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private CandleProjector candleProjector;

    @Autowired
    private RecordingWebSocketBroadcaster broadcaster;

    @Autowired
    private PublicTradeRepository tradeRepository;

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private OrderBookSnapshotRepository snapshotRepository;

    @Autowired
    private MarketDataSequenceRepository sequenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearData() {
        jdbcTemplate.execute("""
            truncate table
                market_data_order_book_deltas,
                market_data_order_book_snapshots,
                market_data_candles,
                market_data_tickers,
                market_data_public_trades,
                market_data_sequences
            cascade
            """);
        broadcaster.clear();
    }

    @Test
    void generatesCandlesFromExecutionEvents() {
        marketDataEventPort.executionCreated(execution("exec-candle-1", 1, "100.00", "0.5"));
        marketDataEventPort.executionCreated(execution("exec-candle-2", 2, "105.00", "0.2"));

        var candle = candleRepository.findAll().getFirst();
        assertThat(candleRepository.count()).isEqualTo(1);
        assertThat(candle.openPrice()).isEqualByComparingTo("100.00");
        assertThat(candle.highPrice()).isEqualByComparingTo("105.00");
        assertThat(candle.lowPrice()).isEqualByComparingTo("100.00");
        assertThat(candle.closePrice()).isEqualByComparingTo("105.00");
        assertThat(candle.volume()).isEqualByComparingTo("0.7");
        assertThat(candle.tradeCount()).isEqualTo(2);

        candleProjector.closeCompletedCandles("BTC-USD", clock.instant().plusSeconds(120));
        assertThat(candleRepository.findAll().getFirst().closed()).isTrue();
        assertThat(broadcaster.broadcasts()).anyMatch(event -> event.topic().equals("/candles.BTC-USD.1m"));
    }

    @Test
    void updatesTickerRollingWindow() {
        marketDataEventPort.executionCreated(execution("exec-ticker-1", 1, "100.00", "0.5"));
        marketDataEventPort.executionCreated(execution("exec-ticker-2", 2, "90.00", "0.1"));
        marketDataEventPort.executionCreated(execution("exec-ticker-3", 3, "110.00", "0.4"));

        var ticker = tickerRepository.findById("BTC-USD").orElseThrow();
        assertThat(ticker.lastPrice()).isEqualByComparingTo("110.00");
        assertThat(ticker.volume24h()).isEqualByComparingTo("1.0");
        assertThat(broadcaster.broadcasts()).anyMatch(event -> event.topic().equals("/ticker.BTC-USD"));
    }

    @Test
    void duplicateExecutionReplayIsIdempotent() {
        MarketDataEventPort.ExecutionCreated event = execution("exec-replay", 1, "100.00", "0.5");

        marketDataEventPort.executionCreated(event);
        marketDataEventPort.executionCreated(event);

        assertThat(tradeRepository.count()).isEqualTo(1);
        assertThat(sequenceRepository.findById("BTC-USD").orElseThrow().lastSequence()).isEqualTo(1);
    }

    @Test
    void sequenceGapIsRejectedForClientResync() {
        assertThatThrownBy(() -> marketDataEventPort.executionCreated(execution("exec-gap", 2, "100.00", "0.5")))
            .isInstanceOf(MarketDataValidationException.class)
            .hasMessageContaining("gap");

        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    void replayRebuildsExecutionProjectionsFromEvents() {
        replayService.replayExecutions(List.of(
            execution("exec-rebuild-2", 2, "101.00", "0.4"),
            execution("exec-rebuild-1", 1, "100.00", "0.6")
        ));

        assertThat(tradeRepository.count()).isEqualTo(2);
        assertThat(candleRepository.findAll().getFirst().volume()).isEqualByComparingTo("1.0");
        assertThat(tickerRepository.findById("BTC-USD").orElseThrow().lastPrice()).isEqualByComparingTo("101.00");
    }

    @Test
    void snapshotProjectionIsReplaySafeAndRebuildable() {
        MarketDataEventPort.BookSnapshotCreated snapshot = new MarketDataEventPort.BookSnapshotCreated(
            "BTC-USD",
            1,
            List.of(new MarketDataEventPort.BookLevel("BID", new BigDecimal("99.00"), new BigDecimal("1.0"))),
            List.of(new MarketDataEventPort.BookLevel("ASK", new BigDecimal("101.00"), new BigDecimal("2.0")))
        );

        marketDataEventPort.bookSnapshotCreated(snapshot);
        marketDataEventPort.bookSnapshotCreated(snapshot);

        assertThat(snapshotRepository.count()).isEqualTo(1);

        replayService.replaySnapshots(List.of(snapshot));

        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.findByMarketSymbolAndMarketSequence("BTC-USD", 1).orElseThrow().bidsJson())
            .contains("\"BID\"");
    }

    @Test
    void bookDeltaProjectionPublishesOrderBookUpdate() {
        marketDataEventPort.bookChanged(new MarketDataEventPort.BookChanged(
            "BTC-USD",
            1,
            "BID",
            new BigDecimal("99.00"),
            new BigDecimal("1.0"),
            "UPSERT"
        ));

        assertThat(countRows("market_data_order_book_deltas", "market_symbol = 'BTC-USD'")).isEqualTo(1);
        assertThat(broadcaster.broadcasts()).anyMatch(event -> event.topic().equals("/book.BTC-USD.delta"));
    }

    private MarketDataEventPort.ExecutionCreated execution(String executionId, long sequence, String price, String quantity) {
        UUID buyer = UUID.nameUUIDFromBytes((executionId + ":buyer").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID seller = UUID.nameUUIDFromBytes((executionId + ":seller").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new MarketDataEventPort.ExecutionCreated(
            executionId,
            "match-" + executionId,
            "BTC-USD",
            buyer,
            seller,
            buyer,
            seller,
            new BigDecimal(quantity),
            new BigDecimal(price),
            sequence
        );
    }

    private long countRows(String tableName, String whereClause) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Long.class);
        return count == null ? 0 : count;
    }
}
