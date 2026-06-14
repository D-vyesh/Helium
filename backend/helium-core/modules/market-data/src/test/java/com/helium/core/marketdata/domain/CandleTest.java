package com.helium.core.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CandleTest {
    @Test
    void rollsOhlcvAcrossTrades() {
        Candle candle = Candle.open(
            "BTC-USD",
            "1m",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:01:00Z"),
            new BigDecimal("100.00"),
            new BigDecimal("0.5")
        );

        candle.addTrade(new BigDecimal("90.00"), new BigDecimal("0.2"));
        candle.addTrade(new BigDecimal("110.00"), new BigDecimal("0.3"));

        assertThat(candle.openPrice()).isEqualByComparingTo("100.00");
        assertThat(candle.highPrice()).isEqualByComparingTo("110.00");
        assertThat(candle.lowPrice()).isEqualByComparingTo("90.00");
        assertThat(candle.closePrice()).isEqualByComparingTo("110.00");
        assertThat(candle.volume()).isEqualByComparingTo("1.0");
        assertThat(candle.tradeCount()).isEqualTo(3);
    }
}
