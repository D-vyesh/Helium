package com.helium.core.app.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.app.api.LiveMarketDataModels.CandleResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketStatsResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketView;
import com.helium.core.app.api.LiveMarketDataModels.OrderBookResponse;
import com.helium.core.app.api.LiveMarketDataModels.StreamStatusResponse;
import com.helium.core.app.api.LiveMarketDataModels.TickerResponse;
import com.helium.core.app.api.LiveMarketDataModels.TradeResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
class LiveMarketDataCache {
    private static final TypeReference<List<MarketView>> MARKETS = new TypeReference<>() {};
    private static final TypeReference<List<TradeResponse>> TRADES = new TypeReference<>() {};
    private static final TypeReference<List<CandleResponse>> CANDLES = new TypeReference<>() {};

    private final RedisInfrastructureService redis;
    private final ObjectMapper objectMapper;
    private final Counter hits;
    private final Counter misses;
    private final ConcurrentMap<String, String> localCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> localExpiresAt = new ConcurrentHashMap<>();

    LiveMarketDataCache(RedisInfrastructureService redis, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.hits = Counter.builder("helium_market_cache_hits_total").register(meterRegistry);
        this.misses = Counter.builder("helium_market_cache_misses_total").register(meterRegistry);
    }

    void putMarkets(List<MarketView> markets, Duration ttl) {
        put("markets", markets, ttl);
    }

    List<MarketView> markets() {
        return get("markets", MARKETS).orElse(List.of());
    }

    void putTicker(String symbol, TickerResponse ticker, Duration ttl) {
        put("ticker:" + symbol, ticker, ttl);
    }

    Optional<TickerResponse> ticker(String symbol) {
        return get("ticker:" + symbol, TickerResponse.class);
    }

    void putStats(String symbol, MarketStatsResponse stats, Duration ttl) {
        put("stats:" + symbol, stats, ttl);
    }

    Optional<MarketStatsResponse> stats(String symbol) {
        return get("stats:" + symbol, MarketStatsResponse.class);
    }

    void putOrderBook(String symbol, OrderBookResponse orderBook, Duration ttl) {
        put("orderbook:" + symbol, orderBook, ttl);
    }

    Optional<OrderBookResponse> orderBook(String symbol) {
        return get("orderbook:" + symbol, OrderBookResponse.class);
    }

    void putTrades(String symbol, List<TradeResponse> trades, Duration ttl) {
        put("trades:" + symbol, trades, ttl);
    }

    List<TradeResponse> trades(String symbol) {
        return get("trades:" + symbol, TRADES).orElse(List.of());
    }

    void putCandles(String symbol, String interval, List<CandleResponse> candles, Duration ttl) {
        put("candles:" + symbol + ":" + interval, candles, ttl);
    }

    List<CandleResponse> candles(String symbol, String interval) {
        return get("candles:" + symbol + ":" + interval, CANDLES).orElse(List.of());
    }

    void putStatus(StreamStatusResponse status, Duration ttl) {
        put("status", status, ttl);
    }

    Optional<StreamStatusResponse> status() {
        return get("status", StreamStatusResponse.class);
    }

    private void put(String key, Object value, Duration ttl) {
        String payload = toJson(value);
        localCache.put(key, payload);
        localExpiresAt.put(key, System.currentTimeMillis() + ttl.toMillis());
        redis.cacheMarketData("live:" + key, payload, ttl);
    }

    private <T> Optional<T> get(String key, Class<T> type) {
        return payload(key).map(value -> fromJson(value, type));
    }

    private <T> Optional<T> get(String key, TypeReference<T> type) {
        return payload(key).map(value -> fromJson(value, type));
    }

    private Optional<String> payload(String key) {
        Optional<String> value = redis.cachedMarketData("live:" + key).or(() -> Optional.ofNullable(localPayload(key)));
        if (value.isPresent()) {
            hits.increment();
        } else {
            misses.increment();
        }
        return value;
    }

    @Nullable
    private String localPayload(String key) {
        Long expiresAt = localExpiresAt.get(key);
        if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
            localCache.remove(key);
            localExpiresAt.remove(key);
            return null;
        }
        return localCache.get(key);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("market cache serialization failed", exception);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("market cache deserialization failed", exception);
        }
    }

    private <T> T fromJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("market cache deserialization failed", exception);
        }
    }
}
