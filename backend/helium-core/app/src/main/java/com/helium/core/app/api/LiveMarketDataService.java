package com.helium.core.app.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.app.api.LiveMarketDataModels.BookOrderView;
import com.helium.core.app.api.LiveMarketDataModels.CandleResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketStatsResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketView;
import com.helium.core.app.api.LiveMarketDataModels.OrderBookResponse;
import com.helium.core.app.api.LiveMarketDataModels.StreamStatusResponse;
import com.helium.core.app.api.LiveMarketDataModels.TickerResponse;
import com.helium.core.app.api.LiveMarketDataModels.TradeResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Service
public class LiveMarketDataService {
    private static final Logger log = LoggerFactory.getLogger(LiveMarketDataService.class);
    private static final String INTERVAL = "1m";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final LiveMarketDataProperties properties;
    private final LiveMarketDataCache cache;
    private final MarketDataWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ScheduledExecutorService reconnectExecutor;
    private final Counter reconnectCounter;
    private final Counter droppedMessageCounter;
    private final Counter snapshotRebuildCounter;
    private final Timer restRefreshTimer;
    private final Timer streamMessageTimer;
    private final ConcurrentMap<String, BookState> books = new ConcurrentHashMap<>();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicLong reconnects = new AtomicLong(0);
    private final AtomicLong droppedMessages = new AtomicLong(0);
    private final AtomicLong snapshotRebuilds = new AtomicLong(0);
    private volatile WebSocketSession streamSession;
    private volatile Instant lastMessageAt;

    public LiveMarketDataService(
        LiveMarketDataProperties properties,
        LiveMarketDataCache cache,
        MarketDataWebSocketHandler webSocketHandler,
        ObjectMapper objectMapper,
        RestClient.Builder restClientBuilder,
        MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.cache = cache;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.requestFactory(requestFactory()).baseUrl(properties.restBaseUrl()).build();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("binance-market-stream-", 0).factory());
        this.reconnectCounter = Counter.builder("helium_market_ws_reconnects_total").register(meterRegistry);
        this.droppedMessageCounter = Counter.builder("helium_market_dropped_messages_total").register(meterRegistry);
        this.snapshotRebuildCounter = Counter.builder("helium_market_snapshot_rebuilds_total").register(meterRegistry);
        this.restRefreshTimer = Timer.builder("helium_market_rest_refresh_seconds").register(meterRegistry);
        this.streamMessageTimer = Timer.builder("helium_market_stream_message_seconds").register(meterRegistry);
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.restTimeout());
        factory.setReadTimeout(properties.restTimeout());
        return factory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        publishStatus();
        if (!properties.enabled()) {
            cache.putMarkets(configuredMarkets(), properties.cacheTtl());
            log.info("Live Binance market data is disabled");
            return;
        }
        refreshAll();
        connectStream();
    }

    @Scheduled(fixedDelayString = "${HELIUM_MARKET_DATA_REST_REFRESH_MS:30000}")
    public void scheduledRefresh() {
        if (properties.enabled()) {
            refreshAll();
        }
    }

    public List<MarketView> markets() {
        List<MarketView> markets = cache.markets();
        return markets.isEmpty() ? configuredMarkets() : markets;
    }

    public Optional<MarketView> market(String symbol) {
        String normalized = normalize(symbol);
        return markets().stream().filter(market -> market.symbol().equals(normalized)).findFirst();
    }

    public Optional<TickerResponse> ticker(String symbol) {
        return cache.ticker(normalize(symbol));
    }

    public Optional<MarketStatsResponse> stats(String symbol) {
        return cache.stats(normalize(symbol));
    }

    public Optional<OrderBookResponse> orderBook(String symbol) {
        return cache.orderBook(normalize(symbol));
    }

    public List<TradeResponse> trades(String symbol) {
        return cache.trades(normalize(symbol));
    }

    public List<CandleResponse> candles(String symbol, String interval) {
        return cache.candles(normalize(symbol), normalizeInterval(interval));
    }

    public StreamStatusResponse status() {
        return cache.status().orElseGet(this::currentStatus);
    }

    public boolean supports(String symbol) {
        String normalized = normalize(symbol);
        return properties.symbols().contains(normalized);
    }

    private void refreshAll() {
        restRefreshTimer.record(() -> {
            cache.putMarkets(loadMarkets(), properties.cacheTtl());
            for (String symbol : properties.symbols()) {
                try {
                    refreshSymbol(symbol);
                } catch (RuntimeException exception) {
                    log.warn("Unable to refresh Binance market data for {}", symbol, exception);
                }
            }
            publishStatus();
        });
    }

    private void refreshSymbol(String symbol) {
        refreshTicker(symbol);
        refreshDepth(symbol, false);
        refreshTrades(symbol);
        refreshCandles(symbol);
    }

    private List<MarketView> loadMarkets() {
        List<MarketView> markets = new ArrayList<>();
        for (String symbol : properties.symbols()) {
            try {
                JsonNode root = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v3/exchangeInfo").queryParam("symbol", symbol).build())
                    .retrieve()
                    .body(JsonNode.class);
                JsonNode binanceMarket = root == null ? null : root.path("symbols").path(0);
                markets.add(binanceMarket == null || binanceMarket.isMissingNode() ? configuredMarket(symbol) : toMarket(binanceMarket));
            } catch (RuntimeException exception) {
                log.warn("Unable to load Binance exchangeInfo for {}", symbol, exception);
                markets.add(configuredMarket(symbol));
            }
        }
        return markets;
    }

    private void refreshTicker(String symbol) {
        JsonNode root = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/ticker/24hr").queryParam("symbol", symbol).build())
            .retrieve()
            .body(JsonNode.class);
        if (root == null) {
            return;
        }
        TickerResponse existing = cache.ticker(symbol).orElse(null);
        BigDecimal bid = existing == null ? ZERO : existing.bestBid();
        BigDecimal ask = existing == null ? ZERO : existing.bestAsk();
        TickerResponse ticker = new TickerResponse(
            symbol,
            decimal(root, "lastPrice"),
            decimal(root, "openPrice"),
            decimal(root, "highPrice"),
            decimal(root, "lowPrice"),
            decimal(root, "volume"),
            decimal(root, "quoteVolume"),
            root.path("count").asLong(0),
            bid,
            ask,
            spread(bid, ask),
            true,
            Instant.now()
        );
        cache.putTicker(symbol, ticker, properties.cacheTtl());
        cache.putStats(symbol, new MarketStatsResponse(
            symbol,
            decimal(root, "priceChange"),
            decimal(root, "priceChangePercent"),
            decimal(root, "weightedAvgPrice"),
            ticker.lastPrice(),
            ticker.highPrice24h(),
            ticker.lowPrice24h(),
            ticker.volume24h(),
            ticker.quoteVolume24h(),
            ticker.tradeCount24h(),
            instant(root.path("openTime").asLong(0)),
            instant(root.path("closeTime").asLong(0)),
            ticker.updatedAt()
        ), properties.cacheTtl());
        broadcast(symbol, "TICKER", "ticker", ticker);
    }

    private void refreshDepth(String symbol, boolean rebuild) {
        JsonNode root = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/depth")
                .queryParam("symbol", symbol)
                .queryParam("limit", properties.orderBookLimit())
                .build())
            .retrieve()
            .body(JsonNode.class);
        if (root == null) {
            return;
        }
        BookState state = books.computeIfAbsent(symbol, ignored -> new BookState());
        synchronized (state) {
            state.bids.clear();
            state.asks.clear();
            root.path("bids").forEach(level -> putLevel(state.bids, level));
            root.path("asks").forEach(level -> putLevel(state.asks, level));
            state.lastUpdateId = root.path("lastUpdateId").asLong(0);
            state.initialized = true;
            publishOrderBook(symbol, state);
        }
        if (rebuild) {
            snapshotRebuilds.incrementAndGet();
            snapshotRebuildCounter.increment();
        }
    }

    private void refreshTrades(String symbol) {
        JsonNode root = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/trades")
                .queryParam("symbol", symbol)
                .queryParam("limit", properties.tradeLimit())
                .build())
            .retrieve()
            .body(JsonNode.class);
        if (root == null || !root.isArray()) {
            return;
        }
        List<TradeResponse> trades = new ArrayList<>();
        root.forEach(node -> trades.add(toTrade(symbol, node)));
        trades.sort(Comparator.comparing(TradeResponse::sequence).reversed());
        cache.putTrades(symbol, trades, properties.cacheTtl());
        broadcast(symbol, "TRADES", "trades", trades);
    }

    private void refreshCandles(String symbol) {
        JsonNode root = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/klines")
                .queryParam("symbol", symbol)
                .queryParam("interval", INTERVAL)
                .queryParam("limit", properties.candleLimit())
                .build())
            .retrieve()
            .body(JsonNode.class);
        if (root == null || !root.isArray()) {
            return;
        }
        List<CandleResponse> candles = new ArrayList<>();
        root.forEach(node -> candles.add(toCandle(symbol, node, true)));
        cache.putCandles(symbol, INTERVAL, candles, properties.cacheTtl());
        broadcast(symbol, "CANDLES", "candles", candles);
    }

    private void connectStream() {
        if (!properties.enabled()) {
            return;
        }
        reconnectScheduled.set(false);
        String streams = properties.symbols().stream()
            .flatMap(symbol -> {
                String lower = symbol.toLowerCase(Locale.ROOT);
                return List.of(
                    lower + "@bookTicker",
                    lower + "@depth@100ms",
                    lower + "@trade",
                    lower + "@miniTicker",
                    lower + "@kline_" + INTERVAL
                ).stream();
            })
            .reduce((left, right) -> left + "/" + right)
            .orElse("");
        String uri = properties.websocketBaseUrl() + "/stream?streams=" + streams;
        new StandardWebSocketClient().execute(new BinanceStreamHandler(), uri).whenComplete((session, exception) -> {
            if (exception != null) {
                connected.set(false);
                log.warn("Unable to connect Binance market stream", exception);
                scheduleReconnect();
            } else {
                streamSession = session;
                connected.set(true);
                reconnectAttempts.set(0);
                publishStatus();
                log.info("Connected Binance market stream for {}", properties.symbols());
            }
        });
    }

    private void scheduleReconnect() {
        if (!properties.enabled() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        connected.set(false);
        long attempts = reconnectAttempts.incrementAndGet();
        long delay = Math.min(
            properties.reconnectInitialDelay().toMillis() * (1L << Math.min(attempts, 5)),
            properties.reconnectMaxDelay().toMillis()
        );
        reconnects.incrementAndGet();
        reconnectCounter.increment();
        publishStatus();
        reconnectExecutor.schedule(this::connectStream, delay, TimeUnit.MILLISECONDS);
    }

    private void handleStreamMessage(String payload) {
        streamMessageTimer.record(() -> {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String stream = root.path("stream").asText("");
                JsonNode data = root.path("data");
                String symbol = data.path("s").asText("").toUpperCase(Locale.ROOT);
                if (!supports(symbol)) {
                    return;
                }
                lastMessageAt = Instant.now();
                if (stream.endsWith("@bookTicker")) {
                    handleBookTicker(symbol, data);
                } else if (stream.contains("@depth")) {
                    handleDepth(symbol, data);
                } else if (stream.endsWith("@trade")) {
                    handleTrade(symbol, data);
                } else if (stream.endsWith("@miniTicker")) {
                    handleMiniTicker(symbol, data);
                } else if (stream.contains("@kline_")) {
                    handleKline(symbol, data.path("k"));
                }
                publishStatus();
            } catch (Exception exception) {
                incrementDroppedMessage();
                log.warn("Unable to process Binance market stream frame", exception);
            }
        });
    }

    private void handleBookTicker(String symbol, JsonNode data) {
        BigDecimal bid = decimal(data, "b");
        BigDecimal ask = decimal(data, "a");
        TickerResponse current = cache.ticker(symbol).orElse(null);
        if (current == null) {
            return;
        }
        TickerResponse updated = new TickerResponse(
            symbol,
            current.lastPrice(),
            current.openPrice24h(),
            current.highPrice24h(),
            current.lowPrice24h(),
            current.volume24h(),
            current.quoteVolume24h(),
            current.tradeCount24h(),
            bid,
            ask,
            spread(bid, ask),
            current.enabled(),
            Instant.now()
        );
        cache.putTicker(symbol, updated, properties.cacheTtl());
        broadcast(symbol, "TICKER", "ticker", updated);
    }

    private void handleMiniTicker(String symbol, JsonNode data) {
        TickerResponse current = cache.ticker(symbol).orElse(null);
        if (current == null) {
            return;
        }
        TickerResponse updated = new TickerResponse(
            symbol,
            decimal(data, "c"),
            current.openPrice24h(),
            decimal(data, "h"),
            decimal(data, "l"),
            decimal(data, "v"),
            decimal(data, "q"),
            current.tradeCount24h(),
            current.bestBid(),
            current.bestAsk(),
            current.spread(),
            current.enabled(),
            Instant.now()
        );
        cache.putTicker(symbol, updated, properties.cacheTtl());
        broadcast(symbol, "TICKER", "ticker", updated);
    }

    private void handleDepth(String symbol, JsonNode data) {
        BookState state = books.computeIfAbsent(symbol, ignored -> new BookState());
        synchronized (state) {
            if (!state.initialized) {
                rebuildSnapshot(symbol);
                return;
            }
            long first = data.path("U").asLong(0);
            long finalUpdate = data.path("u").asLong(0);
            if (finalUpdate <= state.lastUpdateId) {
                return;
            }
            if (first > state.lastUpdateId + 1) {
                incrementDroppedMessage();
                rebuildSnapshot(symbol);
                return;
            }
            data.path("b").forEach(level -> applyLevel(state.bids, level));
            data.path("a").forEach(level -> applyLevel(state.asks, level));
            state.lastUpdateId = finalUpdate;
            publishOrderBook(symbol, state);
        }
    }

    private void handleTrade(String symbol, JsonNode data) {
        TradeResponse trade = new TradeResponse(
            data.path("t").asText(),
            symbol,
            decimal(data, "p"),
            decimal(data, "q"),
            decimal(data, "p").multiply(decimal(data, "q")),
            data.path("m").asBoolean(false),
            data.path("t").asLong(0),
            instant(data.path("T").asLong(0))
        );
        List<TradeResponse> trades = new ArrayList<>(cache.trades(symbol));
        trades.add(0, trade);
        if (trades.size() > properties.tradeLimit()) {
            trades = new ArrayList<>(trades.subList(0, properties.tradeLimit()));
        }
        cache.putTrades(symbol, trades, properties.cacheTtl());
        broadcast(symbol, "TRADES", "trades", trades);
    }

    private void handleKline(String symbol, JsonNode data) {
        CandleResponse candle = toStreamCandle(symbol, data);
        List<CandleResponse> candles = new ArrayList<>(cache.candles(symbol, candle.interval()));
        candles.removeIf(existing -> existing.openTime().equals(candle.openTime()));
        candles.add(candle);
        candles.sort(Comparator.comparing(CandleResponse::openTime));
        if (candles.size() > properties.candleLimit()) {
            candles = new ArrayList<>(candles.subList(candles.size() - properties.candleLimit(), candles.size()));
        }
        cache.putCandles(symbol, candle.interval(), candles, properties.cacheTtl());
        broadcast(symbol, "CANDLES", "candles", candles);
    }

    private void publishOrderBook(String symbol, BookState state) {
        OrderBookResponse response = new OrderBookResponse(
            symbol,
            state.lastUpdateId,
            levels(state.bids, properties.orderBookLimit(), state.lastUpdateId),
            levels(state.asks, properties.orderBookLimit(), state.lastUpdateId),
            Instant.now()
        );
        cache.putOrderBook(symbol, response, properties.cacheTtl());
        broadcast(symbol, "ORDERBOOK", "orderbook", response);
    }

    private void rebuildSnapshot(String symbol) {
        refreshDepth(symbol, true);
    }

    private void incrementDroppedMessage() {
        droppedMessages.incrementAndGet();
        droppedMessageCounter.increment();
    }

    private void broadcast(String symbol, String channel, String eventType, Object payload) {
        webSocketHandler.broadcast(symbol + "/" + channel, eventType, payload);
    }

    private void publishStatus() {
        cache.putStatus(currentStatus(), properties.cacheTtl());
    }

    private StreamStatusResponse currentStatus() {
        return new StreamStatusResponse(
            properties.enabled(),
            connected.get(),
            lastMessageAt,
            reconnects.get(),
            droppedMessages.get(),
            snapshotRebuilds.get(),
            "BINANCE"
        );
    }

    private List<MarketView> configuredMarkets() {
        return properties.symbols().stream().map(this::configuredMarket).toList();
    }

    private MarketView configuredMarket(String symbol) {
        String baseAsset = symbol.endsWith("USDT") ? symbol.substring(0, symbol.length() - 4) : symbol;
        return new MarketView(symbol, baseAsset, "USDT", 8, 8, new BigDecimal("0.00000001"), BigDecimal.ONE, true, "BINANCE");
    }

    private MarketView toMarket(JsonNode node) {
        BigDecimal minQty = new BigDecimal("0.00000001");
        BigDecimal minNotional = BigDecimal.ONE;
        for (JsonNode filter : node.path("filters")) {
            if ("LOT_SIZE".equals(filter.path("filterType").asText())) {
                minQty = decimal(filter, "minQty");
            }
            if ("NOTIONAL".equals(filter.path("filterType").asText()) || "MIN_NOTIONAL".equals(filter.path("filterType").asText())) {
                minNotional = decimal(filter, filter.hasNonNull("minNotional") ? "minNotional" : "notional");
            }
        }
        return new MarketView(
            node.path("symbol").asText(),
            node.path("baseAsset").asText(),
            node.path("quoteAsset").asText(),
            node.path("quotePrecision").asInt(8),
            node.path("baseAssetPrecision").asInt(8),
            minQty,
            minNotional,
            "TRADING".equals(node.path("status").asText()),
            "BINANCE"
        );
    }

    private TradeResponse toTrade(String symbol, JsonNode node) {
        BigDecimal price = decimal(node, "price");
        BigDecimal quantity = decimal(node, "qty");
        return new TradeResponse(
            node.path("id").asText(),
            symbol,
            price,
            quantity,
            price.multiply(quantity),
            node.path("isBuyerMaker").asBoolean(false),
            node.path("id").asLong(0),
            instant(node.path("time").asLong(0))
        );
    }

    private CandleResponse toCandle(String symbol, JsonNode node, boolean closed) {
        return new CandleResponse(
            symbol,
            INTERVAL,
            instant(node.path(0).asLong(0)),
            instant(node.path(6).asLong(0)),
            decimal(node.path(1).asText()),
            decimal(node.path(2).asText()),
            decimal(node.path(3).asText()),
            decimal(node.path(4).asText()),
            decimal(node.path(5).asText()),
            decimal(node.path(7).asText()),
            node.path(8).asLong(0),
            closed
        );
    }

    private CandleResponse toStreamCandle(String symbol, JsonNode node) {
        return new CandleResponse(
            symbol,
            node.path("i").asText(INTERVAL),
            instant(node.path("t").asLong(0)),
            instant(node.path("T").asLong(0)),
            decimal(node, "o"),
            decimal(node, "h"),
            decimal(node, "l"),
            decimal(node, "c"),
            decimal(node, "v"),
            decimal(node, "q"),
            node.path("n").asLong(0),
            node.path("x").asBoolean(false)
        );
    }

    private List<BookOrderView> levels(NavigableMap<BigDecimal, BigDecimal> levels, int limit, long sequence) {
        return levels.entrySet().stream()
            .limit(limit)
            .map(entry -> new BookOrderView(entry.getKey().toPlainString(), entry.getKey(), entry.getValue(), sequence))
            .toList();
    }

    private void putLevel(NavigableMap<BigDecimal, BigDecimal> levels, JsonNode level) {
        levels.put(decimal(level.path(0).asText()), decimal(level.path(1).asText()));
    }

    private void applyLevel(NavigableMap<BigDecimal, BigDecimal> levels, JsonNode level) {
        BigDecimal price = decimal(level.path(0).asText());
        BigDecimal quantity = decimal(level.path(1).asText());
        if (quantity.signum() == 0) {
            levels.remove(price);
        } else {
            levels.put(price, quantity);
        }
    }

    private BigDecimal spread(BigDecimal bid, BigDecimal ask) {
        if (bid == null || ask == null || bid.signum() == 0 || ask.signum() == 0) {
            return ZERO;
        }
        return ask.subtract(bid);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return decimal(node.path(field).asText("0"));
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        return new BigDecimal(value);
    }

    private Instant instant(long epochMillis) {
        return epochMillis <= 0 ? Instant.now() : Instant.ofEpochMilli(epochMillis);
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT).replace("-", "");
    }

    private String normalizeInterval(String interval) {
        return interval == null || interval.isBlank() ? INTERVAL : interval.trim();
    }

    @PreDestroy
    public void stop() {
        connected.set(false);
        WebSocketSession session = streamSession;
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception exception) {
                log.debug("Unable to close Binance market stream cleanly", exception);
            }
        }
        reconnectExecutor.shutdownNow();
    }

    private final class BinanceStreamHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            handleStreamMessage(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            connected.set(false);
            publishStatus();
            scheduleReconnect();
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            connected.set(false);
            publishStatus();
            log.warn("Binance market stream transport error", exception);
            scheduleReconnect();
        }
    }

    private static final class BookState {
        private final NavigableMap<BigDecimal, BigDecimal> bids = new java.util.TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<BigDecimal, BigDecimal> asks = new java.util.TreeMap<>();
        private boolean initialized;
        private long lastUpdateId;
    }
}
