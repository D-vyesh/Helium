package com.helium.core.matching.domain;

public record MatchId(String value) {
    public MatchId {
        value = MatchingText.require(value, "matchId", 160);
    }
}
