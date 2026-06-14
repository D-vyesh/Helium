package com.helium.core.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MarketDataSequenceTest {
    @Test
    void appliesStrictlyContiguousSequences() {
        MarketDataSequence sequence = MarketDataSequence.start("btc-usd", Instant.now());

        sequence.apply(1, Instant.now());
        sequence.apply(2, Instant.now());

        assertThat(sequence.lastSequence()).isEqualTo(2);
    }

    @Test
    void rejectsSequenceGaps() {
        MarketDataSequence sequence = MarketDataSequence.start("BTC-USD", Instant.now());

        assertThatThrownBy(() -> sequence.apply(2, Instant.now()))
            .isInstanceOf(MarketDataValidationException.class)
            .hasMessageContaining("gap");
    }
}
