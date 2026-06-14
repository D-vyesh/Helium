package com.helium.core.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;

@Entity
@Table(name = "matching_sequences")
public class MatchingSequence {
    @Id
    @Column(name = "market_symbol", nullable = false, updatable = false, length = 80)
    private String marketSymbol;

    @Column(name = "current_sequence", nullable = false)
    private long currentSequence;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MatchingSequence() {
    }

    private MatchingSequence(String marketSymbol) {
        this.marketSymbol = MatchingText.market(marketSymbol);
        this.currentSequence = 0;
    }

    public static MatchingSequence start(String marketSymbol) {
        return new MatchingSequence(marketSymbol);
    }

    public long next() {
        currentSequence++;
        return currentSequence;
    }

    public String marketSymbol() {
        return marketSymbol;
    }

    public long currentSequence() {
        return currentSequence;
    }
}
