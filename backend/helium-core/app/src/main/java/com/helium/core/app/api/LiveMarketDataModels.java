package com.helium.core.app.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LiveMarketDataModels {
    private LiveMarketDataModels() {
    }

    public record MarketView(
        String symbol,
        String baseAsset,
        String quoteAsset,
        int priceScale,
        int quantityScale,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        boolean enabled,
        String source
    ) {}

    public record TickerResponse(
        String market,
        BigDecimal lastPrice,
        BigDecimal openPrice24h,
        BigDecimal highPrice24h,
        BigDecimal lowPrice24h,
        BigDecimal volume24h,
        BigDecimal quoteVolume24h,
        long tradeCount24h,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal spread,
        boolean enabled,
        Instant updatedAt
    ) {}

    public record MarketStatsResponse(
        String market,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal weightedAveragePrice,
        BigDecimal lastPrice,
        BigDecimal highPrice24h,
        BigDecimal lowPrice24h,
        BigDecimal volume24h,
        BigDecimal quoteVolume24h,
        long tradeCount24h,
        Instant openTime,
        Instant closeTime,
        Instant updatedAt
    ) {}

    public record OrderBookResponse(
        String marketSymbol,
        long lastUpdateId,
        List<BookOrderView> bids,
        List<BookOrderView> asks,
        Instant updatedAt
    ) {}

    public record BookOrderView(String orderId, BigDecimal price, BigDecimal remainingQuantity, long receivedSequence) {}

    public record TradeResponse(
        String executionId,
        String market,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal quoteQuantity,
        boolean buyerMaker,
        long sequence,
        Instant tradedAt
    ) {}

    public record CandleResponse(
        String market,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal quoteVolume,
        long tradeCount,
        boolean closed
    ) {}

    public record StreamStatusResponse(
        boolean enabled,
        boolean connected,
        Instant lastMessageAt,
        long reconnects,
        long droppedMessages,
        long snapshotRebuilds,
        String source
    ) {}
}
