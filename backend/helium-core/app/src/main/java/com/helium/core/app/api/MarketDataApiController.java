package com.helium.core.app.api;

import com.helium.core.matching.application.OrderBookQueryPort;
import com.helium.core.trading.application.MarketQueryPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/markets")
@Tag(name = "Market Data")
public class MarketDataApiController {
    private final MarketQueryPort marketQueryPort;
    private final OrderBookQueryPort orderBookQueryPort;
    private final JdbcTemplate jdbcTemplate;

    public MarketDataApiController(MarketQueryPort marketQueryPort, OrderBookQueryPort orderBookQueryPort, JdbcTemplate jdbcTemplate) {
        this.marketQueryPort = marketQueryPort;
        this.orderBookQueryPort = orderBookQueryPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<MarketQueryPort.MarketView> markets() {
        return marketQueryPort.getMarkets();
    }

    @GetMapping("/{symbol}/ticker")
    public TickerResponse ticker(@PathVariable String symbol) {
        return jdbcTemplate.query(
            """
            select market_symbol, last_price, open_price_24h, high_price_24h, low_price_24h,
                   volume_24h, quote_volume_24h, trade_count_24h, enabled, updated_at
            from market_data_tickers
            where market_symbol = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return new TickerResponse(symbol.toUpperCase(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, false, null);
                }
                return new TickerResponse(
                    rs.getString("market_symbol"),
                    rs.getBigDecimal("last_price"),
                    rs.getBigDecimal("open_price_24h"),
                    rs.getBigDecimal("high_price_24h"),
                    rs.getBigDecimal("low_price_24h"),
                    rs.getBigDecimal("volume_24h"),
                    rs.getBigDecimal("quote_volume_24h"),
                    rs.getLong("trade_count_24h"),
                    rs.getBoolean("enabled"),
                    rs.getTimestamp("updated_at").toInstant()
                );
            },
            symbol.toUpperCase()
        );
    }

    @GetMapping("/{symbol}/candles")
    public List<CandleResponse> candles(@PathVariable String symbol, @RequestParam(defaultValue = "1m") String interval) {
        return jdbcTemplate.query(
            """
            select market_symbol, interval_name, open_time, close_time, open_price, high_price,
                   low_price, close_price, volume, quote_volume, trade_count, closed
            from market_data_candles
            where market_symbol = ? and interval_name = ?
            order by open_time desc
            limit 500
            """,
            (rs, rowNum) -> new CandleResponse(
                rs.getString("market_symbol"),
                rs.getString("interval_name"),
                rs.getTimestamp("open_time").toInstant(),
                rs.getTimestamp("close_time").toInstant(),
                rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"),
                rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"),
                rs.getBigDecimal("volume"),
                rs.getBigDecimal("quote_volume"),
                rs.getLong("trade_count"),
                rs.getBoolean("closed")
            ),
            symbol.toUpperCase(),
            interval
        );
    }

    @GetMapping("/{symbol}/trades")
    public List<TradeResponse> trades(@PathVariable String symbol) {
        return jdbcTemplate.query(
            """
            select execution_id, market_symbol, price, quantity, market_sequence, traded_at
            from market_data_public_trades
            where market_symbol = ?
            order by market_sequence desc
            limit 200
            """,
            (rs, rowNum) -> new TradeResponse(
                rs.getString("execution_id"),
                rs.getString("market_symbol"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("quantity"),
                rs.getLong("market_sequence"),
                rs.getTimestamp("traded_at").toInstant()
            ),
            symbol.toUpperCase()
        );
    }

    @GetMapping("/{symbol}/orderbook")
    public OrderBookQueryPort.OrderBookView orderBook(@PathVariable String symbol) {
        return orderBookQueryPort.getOrderBook(symbol);
    }

    public record TickerResponse(String market, BigDecimal lastPrice, BigDecimal openPrice24h, BigDecimal highPrice24h, BigDecimal lowPrice24h, BigDecimal volume24h, BigDecimal quoteVolume24h, long tradeCount24h, boolean enabled, Instant updatedAt) {}
    public record CandleResponse(String market, String interval, Instant openTime, Instant closeTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume, BigDecimal quoteVolume, long tradeCount, boolean closed) {}
    public record TradeResponse(String executionId, String market, BigDecimal price, BigDecimal quantity, long sequence, Instant tradedAt) {}
}
