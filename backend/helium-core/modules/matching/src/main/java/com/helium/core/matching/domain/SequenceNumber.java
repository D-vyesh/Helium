package com.helium.core.matching.domain;

public record SequenceNumber(long value) {
    public SequenceNumber {
        if (value < 1) {
            throw new MatchingValidationException("sequence number must be positive");
        }
    }
}
