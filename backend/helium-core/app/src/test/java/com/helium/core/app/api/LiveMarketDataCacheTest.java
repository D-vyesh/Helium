package com.helium.core.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.helium.core.app.api.LiveMarketDataModels.BookOrderView;
import com.helium.core.app.api.LiveMarketDataModels.MarketView;
import com.helium.core.app.api.LiveMarketDataModels.OrderBookResponse;
import com.helium.core.app.api.LiveMarketDataModels.TickerResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;

class LiveMarketDataCacheTest {
    private final LiveMarketDataCache cache = new LiveMarketDataCache(
        new RedisInfrastructureService(new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getIfAvailable() {
                return null;
            }
        }),
        new ObjectMapper().registerModule(new JavaTimeModule()),
        new SimpleMeterRegistry()
    );

    @Test
    void storesLiveTickerAndOrderBookWithoutRedis() {
        TickerResponse ticker = new TickerResponse(
            "BTCUSDT",
            new BigDecimal("64000.12"),
            new BigDecimal("63000.00"),
            new BigDecimal("65000.00"),
            new BigDecimal("62000.00"),
            new BigDecimal("123.45"),
            new BigDecimal("7890000.12"),
            42,
            new BigDecimal("63999.99"),
            new BigDecimal("64000.01"),
            new BigDecimal("0.02"),
            true,
            Instant.parse("2026-07-10T00:00:00Z")
        );
        OrderBookResponse book = new OrderBookResponse(
            "BTCUSDT",
            100,
            List.of(new BookOrderView("63999.99", new BigDecimal("63999.99"), new BigDecimal("0.5"), 100)),
            List.of(new BookOrderView("64000.01", new BigDecimal("64000.01"), new BigDecimal("0.4"), 100)),
            Instant.parse("2026-07-10T00:00:01Z")
        );

        cache.putTicker("BTCUSDT", ticker, Duration.ofSeconds(5));
        cache.putOrderBook("BTCUSDT", book, Duration.ofSeconds(5));

        assertThat(cache.ticker("BTCUSDT")).contains(ticker);
        assertThat(cache.orderBook("BTCUSDT")).contains(book);
    }

    @Test
    void storesSupportedMarkets() {
        MarketView market = new MarketView(
            "ETHUSDT",
            "ETH",
            "USDT",
            8,
            8,
            new BigDecimal("0.0001"),
            new BigDecimal("5"),
            true,
            "BINANCE"
        );

        cache.putMarkets(List.of(market), Duration.ofSeconds(5));

        assertThat(cache.markets()).containsExactly(market);
    }
}
