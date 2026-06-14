package com.helium.core.matching.domain;

public record ExecutionId(String value) {
    public ExecutionId {
        value = MatchingText.require(value, "executionId", 160);
    }
}
