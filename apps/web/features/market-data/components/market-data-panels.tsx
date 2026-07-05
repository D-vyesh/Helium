"use client";

import {
  CandlestickChart,
  MarketTicker,
  OrderBookPanel,
  PriceChangeBadge,
  RecentTradesPanel,
  type BookLevel
} from "@/components/exchange/exchange-components";
import { Badge } from "@/components/ui/badge";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Search } from "@/components/ui/search";
import { heliumApi } from "@/lib/api/client";
import type { BookOrder, TickerResponse } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { formatAmount } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { useMarketStream, type StreamStatus } from "@/lib/ws/market-stream";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useMemo, useRef, useState } from "react";

/**
 * Throttled query invalidation driven by WebSocket events so REST queries
 * refresh when the backend broadcasts market activity.
 */
function useStreamRefresh(queryKey: readonly unknown[], minIntervalMs = 1000) {
  const queryClient = useQueryClient();
  const lastRefresh = useRef(0);
  return useCallback(() => {
    const now = Date.now();
    if (now - lastRefresh.current >= minIntervalMs) {
      lastRefresh.current = now;
      void queryClient.invalidateQueries({ queryKey });
    }
  }, [queryClient, queryKey, minIntervalMs]);
}

export function StreamBadge({ status }: Readonly<{ status: StreamStatus }>) {
  if (status === "connected") return <Badge tone="success">Live</Badge>;
  if (status === "connecting") return <Badge tone="info">Connecting…</Badge>;
  if (status === "unavailable") return <Badge tone="warning">Stream unavailable</Badge>;
  return <Badge tone="danger">Disconnected — reconnecting…</Badge>;
}

export function MarketList() {
  const marketsQuery = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets });
  const [search, setSearch] = useState("");
  const symbols = useMemo(() => (marketsQuery.data ?? []).map((market) => market.symbol), [marketsQuery.data]);
  const tickerQueries = useQueries({
    queries: symbols.map((symbol) => ({
      queryKey: queryKeys.ticker(symbol),
      queryFn: () => heliumApi.ticker(symbol),
      refetchInterval: 5000
    }))
  });
  const tickersBySymbol = useMemo(() => {
    const map = new Map<string, TickerResponse>();
    tickerQueries.forEach((query) => {
      if (query.data) map.set(query.data.market, query.data);
    });
    return map;
  }, [tickerQueries]);

  if (marketsQuery.isLoading) return <LoadingState label="Loading markets" />;
  if (marketsQuery.isError) return <ErrorState title="Could not load markets" error={marketsQuery.error} onRetry={() => void marketsQuery.refetch()} />;
  if (!marketsQuery.data?.length) return <EmptyState title="No markets listed" detail="The backend has no registered trading markets." />;

  const markets = marketsQuery.data.filter((market) => market.symbol.toLowerCase().includes(search.toLowerCase()));
  const tickers = markets.map((market) => tickersBySymbol.get(market.symbol)).filter((ticker): ticker is TickerResponse => Boolean(ticker));

  return (
    <div className="space-y-4">
      <MarketTicker tickers={tickers} />
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <Search className="max-w-sm" onChange={(event) => setSearch(event.target.value)} placeholder="Search markets" value={search} />
        <Badge tone="info">{markets.length} instruments</Badge>
      </div>
      <DataTable
        columns={["Market", "Last Price", "24h Change", "24h Volume", "Status"]}
        rows={markets.map((market) => {
          const ticker = tickersBySymbol.get(market.symbol);
          return [
            <Link className="font-semibold text-cyan-200 hover:text-cyan-100" href={`/trade?symbol=${market.symbol}`} key={market.symbol}>{market.symbol}</Link>,
            <span className="font-mono" key="price">{ticker ? formatAmount(ticker.lastPrice) : "—"}</span>,
            ticker ? <PriceChangeBadge key="change" lastPrice={ticker.lastPrice} openPrice={ticker.openPrice24h} /> : <span key="change">—</span>,
            <span className="font-mono text-slate-300" key="volume">{ticker ? `${formatAmount(ticker.volume24h)} ${market.baseAsset}` : "—"}</span>,
            <Badge key="status" tone={market.enabled ? "success" : "warning"}>{market.enabled ? "Online" : "Paused"}</Badge>
          ];
        })}
      />
    </div>
  );
}

export function MarketSelector({ selected }: Readonly<{ selected: string }>) {
  const query = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets });
  if (query.isLoading) return <LoadingState label="Markets" />;
  if (query.isError) return <ErrorState title="Could not load markets" error={query.error} onRetry={() => void query.refetch()} />;
  return (
    <div className="flex flex-wrap gap-2" aria-label="Market selector">
      {(query.data ?? []).map((market) => (
        <Link
          className={cn(
            "rounded-md border border-border bg-white/5 px-3 py-2 text-sm font-semibold text-muted-foreground transition hover:bg-white/10 hover:text-foreground",
            selected === market.symbol && "border-cyan-300/30 bg-cyan-300/15 text-cyan-100 shadow-glow-cyan"
          )}
          href={`/trade?symbol=${market.symbol}`}
          key={market.symbol}
        >
          {market.symbol}
        </Link>
      ))}
    </div>
  );
}

function toLevels(orders: BookOrder[]): BookLevel[] {
  const byPrice = new Map<number, number>();
  orders.forEach((order) => {
    byPrice.set(order.price, (byPrice.get(order.price) ?? 0) + order.remainingQuantity);
  });
  return [...byPrice.entries()].map(([price, quantity]) => ({ price, quantity, total: price * quantity }));
}

export function OrderBook({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.orderBook(symbol), queryFn: () => heliumApi.orderBook(symbol), refetchInterval: 5000 });
  const refresh = useStreamRefresh(queryKeys.orderBook(symbol));
  const status = useMarketStream(symbol, "orderbook", refresh);

  if (query.isLoading) return <LoadingState label="Loading order book" />;
  if (query.isError) return <ErrorState title="Could not load order book" error={query.error} onRetry={() => void query.refetch()} />;

  const bids = toLevels(query.data?.bids ?? []).sort((left, right) => right.price - left.price);
  const asks = toLevels(query.data?.asks ?? []).sort((left, right) => left.price - right.price);

  return (
    <div className="space-y-2">
      <div className="flex justify-end"><StreamBadge status={status} /></div>
      {!bids.length && !asks.length ? <EmptyState title="Order book is empty" /> : <OrderBookPanel asks={asks} bids={bids} />}
    </div>
  );
}

export function RecentTrades({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.publicTrades(symbol), queryFn: () => heliumApi.publicTrades(symbol), refetchInterval: 5000 });
  const refresh = useStreamRefresh(queryKeys.publicTrades(symbol));
  const status = useMarketStream(symbol, "trades", refresh);

  if (query.isLoading) return <LoadingState label="Loading trades" />;
  if (query.isError) return <ErrorState title="Could not load trades" error={query.error} onRetry={() => void query.refetch()} />;

  return (
    <div className="space-y-2">
      <div className="flex justify-end"><StreamBadge status={status} /></div>
      {!query.data?.length ? <EmptyState title="No recent trades" /> : <RecentTradesPanel trades={query.data} />}
    </div>
  );
}

export function CandleChart({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.candles(symbol), queryFn: () => heliumApi.candles(symbol), refetchInterval: 15000 });
  if (query.isLoading) return <LoadingState label="Loading candles" />;
  if (query.isError) return <ErrorState title="Could not load candles" error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data?.length) return <EmptyState title="No candle data" detail="Candles appear once trades execute in this market." />;
  // Backend returns newest-first; the chart renders oldest to newest.
  const ascending = [...query.data].reverse();
  return <CandlestickChart candles={ascending} />;
}

export function TickerHeader({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.ticker(symbol), queryFn: () => heliumApi.ticker(symbol), refetchInterval: 5000 });
  const refresh = useStreamRefresh(queryKeys.ticker(symbol));
  const status = useMarketStream(symbol, "ticker", refresh);

  if (query.isLoading) return <LoadingState label="Loading ticker" />;
  if (query.isError) return <ErrorState title="Could not load ticker" error={query.error} onRetry={() => void query.refetch()} />;
  const ticker = query.data;
  if (!ticker) return <EmptyState title="No ticker data" />;
  return (
    <div className="glass-panel flex flex-wrap items-center gap-4 rounded-lg px-4 py-3 text-sm">
      <span className="font-semibold text-foreground">{ticker.market}</span>
      <span className="font-mono text-lg text-slate-100">{formatAmount(ticker.lastPrice)}</span>
      <PriceChangeBadge lastPrice={ticker.lastPrice} openPrice={ticker.openPrice24h} />
      <span className="text-muted-foreground">24h High <span className="font-mono text-slate-300">{formatAmount(ticker.highPrice24h)}</span></span>
      <span className="text-muted-foreground">24h Low <span className="font-mono text-slate-300">{formatAmount(ticker.lowPrice24h)}</span></span>
      <span className="text-muted-foreground">24h Volume <span className="font-mono text-slate-300">{formatAmount(ticker.volume24h)}</span></span>
      <span className="ml-auto"><StreamBadge status={status} /></span>
    </div>
  );
}
