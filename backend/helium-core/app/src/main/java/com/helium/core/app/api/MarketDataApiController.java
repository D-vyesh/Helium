package com.helium.core.app.api;

import com.helium.core.app.api.LiveMarketDataModels.CandleResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketStatsResponse;
import com.helium.core.app.api.LiveMarketDataModels.MarketView;
import com.helium.core.app.api.LiveMarketDataModels.OrderBookResponse;
import com.helium.core.app.api.LiveMarketDataModels.StreamStatusResponse;
import com.helium.core.app.api.LiveMarketDataModels.TickerResponse;
import com.helium.core.app.api.LiveMarketDataModels.TradeResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/markets")
@Tag(name = "Market Data")
public class MarketDataApiController {
    private final LiveMarketDataService marketData;

    public MarketDataApiController(LiveMarketDataService marketData) {
        this.marketData = marketData;
    }

    @GetMapping
    public List<MarketView> markets() {
        return marketData.markets();
    }

    @GetMapping("/status")
    public StreamStatusResponse status() {
        return marketData.status();
    }

    @GetMapping("/{symbol}")
    public MarketView market(@PathVariable String symbol) {
        return marketData.market(symbol).orElseThrow(() -> unsupported(symbol));
    }

    @GetMapping("/{symbol}/ticker")
    public TickerResponse ticker(@PathVariable String symbol) {
        ensureSupported(symbol);
        return marketData.ticker(symbol).orElseThrow(() -> unavailable(symbol, "ticker"));
    }

    @GetMapping("/{symbol}/stats")
    public MarketStatsResponse stats(@PathVariable String symbol) {
        ensureSupported(symbol);
        return marketData.stats(symbol).orElseThrow(() -> unavailable(symbol, "stats"));
    }

    @GetMapping("/{symbol}/candles")
    public List<CandleResponse> candles(@PathVariable String symbol, @RequestParam(defaultValue = "1m") String interval) {
        ensureSupported(symbol);
        List<CandleResponse> candles = marketData.candles(symbol, interval);
        if (candles.isEmpty()) {
            throw unavailable(symbol, "candles");
        }
        return candles;
    }

    @GetMapping("/{symbol}/trades")
    public List<TradeResponse> trades(@PathVariable String symbol) {
        ensureSupported(symbol);
        List<TradeResponse> trades = marketData.trades(symbol);
        if (trades.isEmpty()) {
            throw unavailable(symbol, "trades");
        }
        return trades;
    }

    @GetMapping("/{symbol}/orderbook")
    public OrderBookResponse orderBook(@PathVariable String symbol) {
        ensureSupported(symbol);
        return marketData.orderBook(symbol).orElseThrow(() -> unavailable(symbol, "order book"));
    }

    private void ensureSupported(String symbol) {
        if (!marketData.supports(symbol)) {
            throw unsupported(symbol);
        }
    }

    private ResponseStatusException unsupported(String symbol) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported market symbol: " + symbol);
    }

    private ResponseStatusException unavailable(String symbol, String dataSet) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Live " + dataSet + " data is not available for " + symbol);
    }
}
