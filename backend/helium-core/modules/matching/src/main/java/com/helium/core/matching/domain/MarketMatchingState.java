package com.helium.core.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "matching_market_state")
public class MarketMatchingState {
    @Id
    @Column(name = "market_symbol", nullable = false, updatable = false, length = 80)
    private String marketSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private MarketMatchingStatus status;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarketMatchingState() {
    }

    private MarketMatchingState(String marketSymbol, Instant now) {
        this.marketSymbol = MatchingText.market(marketSymbol);
        this.status = MarketMatchingStatus.ACTIVE;
        this.lastSequence = 0;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public static MarketMatchingState start(String marketSymbol, Instant now) {
        return new MarketMatchingState(marketSymbol, now);
    }

    public void record(long sequence, Instant now) {
        if (sequence < lastSequence) {
            throw new MatchingValidationException("matching state sequence cannot move backward");
        }
        this.lastSequence = sequence;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public boolean active() {
        return status == MarketMatchingStatus.ACTIVE;
    }

    public MarketMatchingStatus status() {
        return status;
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void halt(Instant now) {
        if (this.status == MarketMatchingStatus.HALTED) return;
        this.status = MarketMatchingStatus.HALTED;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void startAuction(Instant now) {
        if (this.status == MarketMatchingStatus.AUCTION) return;
        this.status = MarketMatchingStatus.AUCTION;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void resume(Instant now) {
        if (this.status == MarketMatchingStatus.ACTIVE) return;
        this.status = MarketMatchingStatus.ACTIVE;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
