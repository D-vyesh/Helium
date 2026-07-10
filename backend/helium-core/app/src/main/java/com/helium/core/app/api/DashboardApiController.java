package com.helium.core.app.api;

import com.helium.core.app.api.LiveMarketDataModels.CandleResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketView;
import com.helium.core.app.api.LiveMarketDataModels.StreamStatusResponse;
import com.helium.core.app.api.LiveMarketDataModels.TickerResponse;
import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardApiController {
    private static final MathContext MONEY_CONTEXT = new MathContext(38, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final List<String> DEFAULT_WATCHLIST = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT");

    private final TrustedActorProvider trustedActorProvider;
    private final ApiReadService readService;
    private final LiveMarketDataService marketData;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DashboardApiController(
        TrustedActorProvider trustedActorProvider,
        ApiReadService readService,
        LiveMarketDataService marketData,
        JdbcTemplate jdbcTemplate,
        Clock clock
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.readService = readService;
        this.marketData = marketData;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        UUID userId = requireUserId();
        PortfolioResponse portfolio = portfolio(userId);
        List<MarketCardResponse> markets = marketOverview();
        return new DashboardResponse(
            portfolio,
            watchlist(userId),
            markets,
            topMovers(markets),
            activity(userId),
            exchangeStatus(markets)
        );
    }

    @GetMapping("/portfolio")
    @Transactional(readOnly = true)
    public PortfolioResponse portfolio() {
        return portfolio(requireUserId());
    }

    @GetMapping("/markets")
    @Transactional(readOnly = true)
    public List<MarketCardResponse> markets() {
        return marketOverview();
    }

    @GetMapping("/activity")
    @Transactional(readOnly = true)
    public List<ActivityItemResponse> activity() {
        return activity(requireUserId());
    }

    @GetMapping("/watchlist")
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> watchlist() {
        return watchlist(requireUserId());
    }

    @PostMapping("/watchlist")
    @Transactional
    public List<WatchlistItemResponse> upsertWatchlistItem(@Valid @RequestBody WatchlistItemRequest request) {
        UUID userId = requireUserId();
        String marketSymbol = internalMarketSymbol(request.marketSymbol());
        if (!marketData.supports(request.marketSymbol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported market symbol");
        }
        jdbcTemplate.update(
            """
            insert into dashboard_watchlist_items (id, user_id, market_symbol, pinned, sort_order, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (user_id, market_symbol) do update
                set pinned = excluded.pinned,
                    sort_order = excluded.sort_order,
                    updated_at = excluded.updated_at
            """,
            UUID.randomUUID(),
            userId,
            marketSymbol,
            request.pinned(),
            Math.max(0, request.sortOrder()),
            clock.instant(),
            clock.instant()
        );
        return watchlist(userId);
    }

    @DeleteMapping("/watchlist/{symbol}")
    @Transactional
    public List<WatchlistItemResponse> removeWatchlistItem(@PathVariable String symbol) {
        UUID userId = requireUserId();
        jdbcTemplate.update(
            "delete from dashboard_watchlist_items where user_id = ? and market_symbol = ?",
            userId,
            internalMarketSymbol(symbol)
        );
        return watchlist(userId);
    }

    private PortfolioResponse portfolio(UUID userId) {
        List<ApiReadService.BalanceDto> balances = readService.balances(userId);
        Map<String, AcquisitionPrice> acquisitionPrices = acquisitionPrices(userId);

        List<PortfolioAssetResponse> draftAssets = balances.stream()
            .map(balance -> portfolioAsset(balance, acquisitionPrices.get(balance.asset())))
            .toList();

        BigDecimal portfolioValue = draftAssets.stream()
            .map(PortfolioAssetResponse::marketValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyChange = draftAssets.stream()
            .map(PortfolioAssetResponse::dailyChange)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PortfolioAssetResponse> assets = draftAssets.stream()
            .map(asset -> withAllocation(asset, portfolioValue))
            .toList();

        BigDecimal dailyChangePercent = percentage(dailyChange, portfolioValue.subtract(dailyChange));
        return new PortfolioResponse(
            portfolioValue,
            dailyChange,
            dailyChangePercent,
            assets.size(),
            assets
        );
    }

    private PortfolioAssetResponse portfolioAsset(ApiReadService.BalanceDto balance, AcquisitionPrice acquisitionPrice) {
        BigDecimal total = balance.available().add(balance.locked());
        PriceSnapshot price = price(balance.asset());
        BigDecimal marketValue = price.currentPrice() == null ? null : total.multiply(price.currentPrice(), MONEY_CONTEXT);
        BigDecimal dailyChange = price.openPrice24h() == null ? null : total.multiply(price.currentPrice().subtract(price.openPrice24h()), MONEY_CONTEXT);
        BigDecimal unrealizedPnl = acquisitionPrice == null || price.currentPrice() == null
            ? null
            : total.multiply(price.currentPrice().subtract(acquisitionPrice.averagePrice()), MONEY_CONTEXT);
        return new PortfolioAssetResponse(
            balance.asset(),
            balance.available(),
            balance.locked(),
            total,
            price.currentPrice(),
            marketValue,
            BigDecimal.ZERO,
            price.priceChangePercent24h(),
            acquisitionPrice == null ? null : acquisitionPrice.averagePrice(),
            unrealizedPnl,
            dailyChange,
            price.updatedAt()
        );
    }

    private PortfolioAssetResponse withAllocation(PortfolioAssetResponse asset, BigDecimal portfolioValue) {
        BigDecimal allocation = asset.marketValue() == null || portfolioValue.signum() == 0
            ? BigDecimal.ZERO
            : asset.marketValue().multiply(ONE_HUNDRED, MONEY_CONTEXT).divide(portfolioValue, 8, RoundingMode.HALF_UP);
        return new PortfolioAssetResponse(
            asset.asset(),
            asset.available(),
            asset.locked(),
            asset.total(),
            asset.currentPrice(),
            asset.marketValue(),
            allocation,
            asset.priceChangePercent24h(),
            asset.averageAcquisitionPrice(),
            asset.unrealizedPnl(),
            asset.dailyChange(),
            asset.priceUpdatedAt()
        );
    }

    private PriceSnapshot price(String asset) {
        if ("USDT".equals(asset)) {
            return new PriceSnapshot(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, clock.instant());
        }
        return marketData.ticker(asset + "USDT")
            .map(ticker -> new PriceSnapshot(
                ticker.lastPrice(),
                ticker.openPrice24h(),
                percentage(ticker.lastPrice().subtract(ticker.openPrice24h()), ticker.openPrice24h()),
                ticker.updatedAt()
            ))
            .orElse(new PriceSnapshot(null, null, null, null));
    }

    private Map<String, AcquisitionPrice> acquisitionPrices(UUID userId) {
        return jdbcTemplate.query(
            """
            select replace(settlement.market_symbol, '-USDT', '') as asset,
                   sum(settlement.quantity * settlement.price) / nullif(sum(settlement.quantity), 0) as average_price,
                   sum(settlement.quantity) as acquired_quantity
            from trading_settlement_instructions settlement
            join trading_orders orders on orders.id = settlement.buyer_order_id
            where orders.user_id = ?
              and settlement.market_symbol like '%-USDT'
            group by replace(settlement.market_symbol, '-USDT', '')
            """,
            (rs, rowNum) -> new AcquisitionPrice(
                rs.getString("asset"),
                rs.getBigDecimal("average_price"),
                rs.getBigDecimal("acquired_quantity")
            ),
            userId
        ).stream().collect(Collectors.toMap(AcquisitionPrice::asset, Function.identity()));
    }

    private List<MarketCardResponse> marketOverview() {
        return marketData.markets().stream()
            .map(this::marketCard)
            .sorted(Comparator.comparing(MarketCardResponse::marketSymbol))
            .toList();
    }

    private MarketCardResponse marketCard(MarketView market) {
        Optional<TickerResponse> ticker = marketData.ticker(market.symbol());
        List<BigDecimal> miniChart = marketData.candles(market.symbol(), "1m").stream()
            .sorted(Comparator.comparing(CandleResponse::openTime))
            .map(CandleResponse::close)
            .limit(60)
            .toList();
        BigDecimal changePercent = ticker
            .map(item -> percentage(item.lastPrice().subtract(item.openPrice24h()), item.openPrice24h()))
            .orElse(null);
        return new MarketCardResponse(
            market.symbol(),
            market.baseAsset(),
            market.quoteAsset(),
            ticker.map(TickerResponse::lastPrice).orElse(null),
            changePercent,
            ticker.map(TickerResponse::highPrice24h).orElse(null),
            ticker.map(TickerResponse::lowPrice24h).orElse(null),
            ticker.map(TickerResponse::volume24h).orElse(null),
            ticker.map(TickerResponse::quoteVolume24h).orElse(null),
            market.enabled() ? "TRADING" : "PAUSED",
            ticker.map(TickerResponse::bestBid).orElse(null),
            ticker.map(TickerResponse::bestAsk).orElse(null),
            ticker.map(TickerResponse::spread).orElse(null),
            ticker.map(TickerResponse::updatedAt).orElse(null),
            miniChart
        );
    }

    private List<MarketCardResponse> topMovers(List<MarketCardResponse> markets) {
        return markets.stream()
            .filter(market -> market.priceChangePercent24h() != null)
            .sorted((left, right) -> right.priceChangePercent24h().abs().compareTo(left.priceChangePercent24h().abs()))
            .limit(5)
            .toList();
    }

    private ExchangeStatusResponse exchangeStatus(List<MarketCardResponse> markets) {
        StreamStatusResponse status = marketData.status();
        Instant lastSync = markets.stream()
            .map(MarketCardResponse::updatedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(status.lastMessageAt());
        return new ExchangeStatusResponse(
            status.connected(),
            status.source(),
            lastSync,
            status.reconnects(),
            status.droppedMessages(),
            markets.stream().filter(market -> "TRADING".equals(market.marketStatus())).count()
        );
    }

    private List<WatchlistItemResponse> watchlist(UUID userId) {
        List<WatchlistItemResponse> persisted = jdbcTemplate.query(
            """
            select item.market_symbol, item.pinned, item.sort_order, item.created_at
            from dashboard_watchlist_items item
            where item.user_id = ?
            order by item.pinned desc, item.sort_order asc, item.created_at desc
            """,
            (rs, rowNum) -> watchlistItem(rs),
            userId
        );
        if (!persisted.isEmpty()) {
            return persisted;
        }
        return DEFAULT_WATCHLIST.stream()
            .filter(marketData::supports)
            .map(symbol -> new WatchlistItemResponse(symbol, false, DEFAULT_WATCHLIST.indexOf(symbol), null, marketCard(marketData.market(symbol).orElseThrow())))
            .toList();
    }

    private WatchlistItemResponse watchlistItem(ResultSet rs) throws SQLException {
        String externalSymbol = externalMarketSymbol(rs.getString("market_symbol"));
        MarketCardResponse market = marketData.market(externalSymbol).map(this::marketCard).orElse(null);
        return new WatchlistItemResponse(
            externalSymbol,
            rs.getBoolean("pinned"),
            rs.getInt("sort_order"),
            rs.getTimestamp("created_at").toInstant(),
            market
        );
    }

    private List<ActivityItemResponse> activity(UUID userId) {
        return jdbcTemplate.query(
            """
            select * from (
                select ('auth-' || id::text) as id, 'SECURITY' as category, event_type as event_type,
                       coalesce(details, event_type) as summary, occurred_at
                from auth_security_audit_events
                where user_id = ?
                union all
                select ('order-' || history.id::text) as id, 'TRADING' as category, history.status as event_type,
                       history.details as summary, history.occurred_at
                from trading_order_history history
                join trading_orders orders on orders.id = history.order_id
                where orders.user_id = ?
                union all
                select ('trade-' || settlement.id::text) as id, 'TRADING' as category, 'TRADE_EXECUTED' as event_type,
                       settlement.market_symbol || ' ' || settlement.quantity || ' @ ' || settlement.price as summary,
                       settlement.created_at as occurred_at
                from trading_settlement_instructions settlement
                join trading_orders orders on orders.id in (settlement.buyer_order_id, settlement.seller_order_id)
                where orders.user_id = ?
                union all
                select ('deposit-' || id::text) as id, 'WALLET' as category, ('DEPOSIT_' || status) as event_type,
                       asset_code || ' deposit ' || amount || ' on ' || network_code as summary, detected_at as occurred_at
                from wallet_deposits
                where user_id = ?
                union all
                select ('withdrawal-' || id::text) as id, 'WALLET' as category, ('WITHDRAWAL_' || status) as event_type,
                       asset_code || ' withdrawal ' || amount || ' on ' || network_code as summary, requested_at as occurred_at
                from wallet_withdrawals
                where user_id = ?
                union all
                select ('exchange-' || id::text) as id, category, event_type, summary, created_at as occurred_at
                from exchange_activity_events
                where user_id = ?
            ) activity
            order by occurred_at desc
            limit 80
            """,
            (rs, rowNum) -> new ActivityItemResponse(
                rs.getString("id"),
                rs.getString("category"),
                rs.getString("event_type"),
                rs.getString("summary"),
                rs.getTimestamp("occurred_at").toInstant()
            ),
            userId,
            userId,
            userId,
            userId,
            userId,
            userId
        );
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.multiply(ONE_HUNDRED, MONEY_CONTEXT).divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private static String internalMarketSymbol(String symbol) {
        String value = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT).replace("-", "");
        if (value.endsWith("USDT") && value.length() > 4) {
            return value.substring(0, value.length() - 4) + "-USDT";
        }
        return value;
    }

    private static String externalMarketSymbol(String symbol) {
        return symbol == null ? "" : symbol.replace("-", "").toUpperCase(Locale.ROOT);
    }

    public record DashboardResponse(
        PortfolioResponse portfolio,
        List<WatchlistItemResponse> watchlist,
        List<MarketCardResponse> markets,
        List<MarketCardResponse> topMovers,
        List<ActivityItemResponse> activity,
        ExchangeStatusResponse exchangeStatus
    ) {}

    public record PortfolioResponse(
        BigDecimal totalValue,
        BigDecimal dailyChange,
        BigDecimal dailyChangePercent,
        long assetCount,
        List<PortfolioAssetResponse> assets
    ) {}

    public record PortfolioAssetResponse(
        String asset,
        BigDecimal available,
        BigDecimal locked,
        BigDecimal total,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal allocationPercent,
        BigDecimal priceChangePercent24h,
        BigDecimal averageAcquisitionPrice,
        BigDecimal unrealizedPnl,
        BigDecimal dailyChange,
        Instant priceUpdatedAt
    ) {}

    public record MarketCardResponse(
        String marketSymbol,
        String baseAsset,
        String quoteAsset,
        BigDecimal currentPrice,
        BigDecimal priceChangePercent24h,
        BigDecimal highPrice24h,
        BigDecimal lowPrice24h,
        BigDecimal volume24h,
        BigDecimal quoteVolume24h,
        String marketStatus,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal spread,
        Instant updatedAt,
        List<BigDecimal> miniChart
    ) {}

    public record WatchlistItemResponse(
        String marketSymbol,
        boolean pinned,
        int sortOrder,
        Instant createdAt,
        MarketCardResponse market
    ) {}

    public record WatchlistItemRequest(@NotBlank @Size(max = 80) String marketSymbol, boolean pinned, int sortOrder) {}

    public record ActivityItemResponse(String id, String category, String eventType, String summary, Instant occurredAt) {}

    public record ExchangeStatusResponse(
        boolean connected,
        String source,
        Instant lastSynchronization,
        long reconnects,
        long droppedMessages,
        long activeMarkets
    ) {}

    private record PriceSnapshot(BigDecimal currentPrice, BigDecimal openPrice24h, BigDecimal priceChangePercent24h, Instant updatedAt) {}
    private record AcquisitionPrice(String asset, BigDecimal averagePrice, BigDecimal acquiredQuantity) {}
}
