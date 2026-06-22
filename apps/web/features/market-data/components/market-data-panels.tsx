"use client";

import {
  CandlestickChart,
  MarketTicker,
  OrderBook as ExchangeOrderBook,
  PriceChangeBadge,
  RecentTrades as ExchangeRecentTrades
} from "@/components/exchange/exchange-components";
import { Badge } from "@/components/ui/badge";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Search } from "@/components/ui/search";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { cn } from "@/lib/utils/cn";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState } from "react";

export function MarketList() {
  const query = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets });
  const [search, setSearch] = useState("");
  const markets = useMemo(
    () => (query.data ?? []).filter((market) => market.symbol.toLowerCase().includes(search.toLowerCase())),
    [query.data, search]
  );
  if (query.isLoading) return <LoadingState label="Loading markets" />;
  if (query.isError) return <ErrorState title="Could not load markets" />;
  if (!query.data?.length) return <EmptyState title="No markets listed" />;
  return (
    <div className="space-y-4">
      <MarketTicker markets={query.data} />
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <Search className="max-w-sm" onChange={(event) => setSearch(event.target.value)} placeholder="Search markets" value={search} />
        <Badge tone="info">{markets.length} instruments</Badge>
      </div>
      <DataTable
        columns={["Market", "Last Price", "24h Change", "24h Volume", "Status"]}
        rows={markets.map((market) => [
          <Link className="font-semibold text-cyan-200 hover:text-cyan-100" href={`/trade?symbol=${market.symbol}`} key={market.symbol}>{market.symbol}</Link>,
          <span className="font-mono" key="price">{market.lastPrice}</span>,
          <PriceChangeBadge key="change" value={market.change24h} />,
          <span className="font-mono text-slate-300" key="volume">{market.volume24h}</span>,
          <Badge key="status" tone={market.enabled ? "success" : "warning"}>{market.enabled ? "Online" : "Paused"}</Badge>
        ])}
      />
    </div>
  );
}

export function MarketSelector({ selected }: Readonly<{ selected: string }>) {
  const query = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets });
  if (query.isLoading) return <LoadingState label="Markets" />;
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

export function OrderBook({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.orderBook(symbol), queryFn: () => heliumApi.orderBook(symbol), refetchInterval: 3000 });
  if (query.isLoading) return <LoadingState label="Loading order book" />;
  if (query.isError) return <ErrorState title="Could not load order book" />;
  return <ExchangeOrderBook asks={query.data?.asks ?? []} bids={query.data?.bids ?? []} />;
}

export function RecentTrades({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.publicTrades(symbol), queryFn: () => heliumApi.publicTrades(symbol), refetchInterval: 3000 });
  if (query.isLoading) return <LoadingState label="Loading trades" />;
  if (query.isError) return <ErrorState title="Could not load trades" />;
  if (!query.data?.length) return <EmptyState title="No recent trades" />;
  return <ExchangeRecentTrades trades={query.data} />;
}

export function CandlestickPlaceholder({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.candles(symbol), queryFn: () => heliumApi.candles(symbol) });
  const candles = query.data ?? [];
  if (query.isLoading) return <LoadingState label="Loading candles" />;
  return (
    <CandlestickChart candles={candles} />
  );
}
