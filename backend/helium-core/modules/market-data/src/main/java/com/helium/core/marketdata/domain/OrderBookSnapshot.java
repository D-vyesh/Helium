package com.helium.core.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_data_order_book_snapshots")
public class OrderBookSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "market_symbol", nullable = false, length = 41)
    private String marketSymbol;

    @Column(name = "market_sequence", nullable = false)
    private long marketSequence;

    @Column(name = "bids_json", nullable = false, columnDefinition = "text")
    private String bidsJson;

    @Column(name = "asks_json", nullable = false, columnDefinition = "text")
    private String asksJson;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderBookSnapshot() {
    }

    private OrderBookSnapshot(String marketSymbol, long marketSequence, String bidsJson, String asksJson, String snapshotHash, Instant createdAt) {
        this.marketSymbol = MarketSymbol.normalize(marketSymbol);
        if (marketSequence <= 0) {
            throw new MarketDataValidationException("market sequence must be positive");
        }
        this.marketSequence = marketSequence;
        this.bidsJson = required(bidsJson, "bidsJson");
        this.asksJson = required(asksJson, "asksJson");
        this.snapshotHash = required(snapshotHash, "snapshotHash");
        this.createdAt = createdAt;
    }

    public static OrderBookSnapshot create(String marketSymbol, long marketSequence, String bidsJson, String asksJson, String snapshotHash, Instant createdAt) {
        return new OrderBookSnapshot(marketSymbol, marketSequence, bidsJson, asksJson, snapshotHash, createdAt);
    }

    public boolean samePayload(String hash) {
        return snapshotHash.equals(hash);
    }

    public String bidsJson() {
        return bidsJson;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MarketDataValidationException(field + " is required");
        }
        return value;
    }
}
