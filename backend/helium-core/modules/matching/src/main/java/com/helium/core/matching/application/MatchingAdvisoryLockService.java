package com.helium.core.matching.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Serialises matching operations per market using PostgreSQL advisory locks.
 *
 * <h2>Architecture: Single-Node, DB-Serialised Matching</h2>
 *
 * <p>Every call to {@link #lockMarket(String)} acquires a transaction-scoped
 * advisory lock keyed on the market symbol. This guarantees that only one
 * thread processes orders for a given market at any instant, even across
 * multiple application instances. This is <b>correct by construction</b> —
 * no race conditions, no double-fills, no sequence gaps.
 *
 * <h3>Throughput Ceiling</h3>
 *
 * <p>The DB round-trip for lock acquisition + order read + fill write limits
 * throughput to approximately <b>3,000–5,000 orders/sec per market</b> under
 * optimal conditions (co-located Postgres, connection pooling, minimal WAL
 * pressure). This is sufficient for early-to-mid production volumes.
 *
 * <p>For reference, Binance's matching engine processes &gt;1,000,000 orders/sec
 * using a purpose-built in-memory engine with a Write-Ahead Sequence Log (WASL)
 * and deterministic replay. That architecture eliminates all DB I/O from the
 * critical matching path.
 *
 * <h3>Migration Roadmap (when &gt;5K orders/sec is required)</h3>
 *
 * <ol>
 *   <li><b>Phase 1 — In-Memory Order Book</b>: Replace the
 *       {@code BookOrderRepository.findMatchableForUpdate} queries with an
 *       in-memory {@code ConcurrentSkipListMap} per market. Persist fills
 *       asynchronously via a write-ahead log (e.g., Kafka topic per market).</li>
 *   <li><b>Phase 2 — Market Partitioning</b>: Assign each market to a
 *       dedicated matching process (single-threaded per market, horizontally
 *       scaled across markets). Use consistent hashing on market symbol for
 *       routing. This eliminates the advisory lock entirely.</li>
 *   <li><b>Phase 3 — LMAX Disruptor / Ring Buffer</b>: For ultra-low-latency
 *       requirements (&gt;100K orders/sec per market), adopt a mechanical
 *       sympathy approach: single-writer ring buffer, pre-allocated objects,
 *       no GC pressure in the hot path. Sequence numbers become offsets into
 *       the ring buffer.</li>
 * </ol>
 *
 * <p>The current advisory-lock approach can coexist with Phase 1 during a
 * gradual migration — the lock acts as a fallback consistency mechanism
 * while the in-memory engine is validated.
 *
 * @see SubmitOrderService#submit  the primary consumer of this lock
 * @see OrderBook                  price-time priority sorting (FIFO)
 */
@Service
class MatchingAdvisoryLockService {
    private final JdbcTemplate jdbcTemplate;

    MatchingAdvisoryLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void lockMarket(String marketSymbol) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtext(?), hashtext(?))",
            statement -> {
                statement.setString(1, "matching:market");
                statement.setString(2, marketSymbol);
            },
            resultSet -> null
        );
    }
}

