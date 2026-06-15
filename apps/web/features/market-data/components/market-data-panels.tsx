"use client";

import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";

export function MarketList() {
  const query = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets });
  if (query.isLoading) return <LoadingState label="Loading markets" />;
  if (query.isError) return <ErrorState title="Could not load markets" />;
  if (!query.data?.length) return <EmptyState title="No markets listed" />;
  return (
    <DataTable
      columns={["Market", "Last Price", "24h Change", "24h Volume", "Status"]}
      rows={query.data.map((market) => [
        <Link className="font-medium text-cyan-300" href={`/trade?symbol=${market.symbol}`} key={market.symbol}>{market.symbol}</Link>,
        market.lastPrice,
        <span className={market.change24h.startsWith("-") ? "text-red-300" : "text-emerald-300"} key="change">{market.change24h}</span>,
        market.volume24h,
        market.enabled ? "Enabled" : "Disabled"
      ])}
    />
  );
}

export function MarketSelector({ selected }: Readonly<{ selected: string }>) {
  const query = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets });
  if (query.isLoading) return <LoadingState label="Markets" />;
  return (
    <div className="flex flex-wrap gap-2">
      {(query.data ?? []).map((market) => (
        <Link
          className={`rounded px-3 py-2 text-sm ${selected === market.symbol ? "bg-cyan-400 text-slate-950" : "border border-slate-700 text-slate-200"}`}
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
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Order Book</h2>
      <div className="grid gap-4 md:grid-cols-2">
        <BookSide title="Bids" rows={query.data?.bids ?? []} tone="text-emerald-300" />
        <BookSide title="Asks" rows={query.data?.asks ?? []} tone="text-red-300" />
      </div>
    </section>
  );
}

export function RecentTrades({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.publicTrades(symbol), queryFn: () => heliumApi.publicTrades(symbol), refetchInterval: 3000 });
  if (query.isLoading) return <LoadingState label="Loading trades" />;
  if (query.isError) return <ErrorState title="Could not load trades" />;
  if (!query.data?.length) return <EmptyState title="No recent trades" />;
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Recent Trades</h2>
      <div className="space-y-2 text-sm">
        {query.data.map((trade) => (
          <div className="grid grid-cols-4 gap-2" key={trade.id}>
            <span className={trade.side === "BUY" ? "text-emerald-300" : "text-red-300"}>{trade.price}</span>
            <span>{trade.quantity}</span>
            <span>{trade.side}</span>
            <span className="text-slate-500">{trade.time}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

export function CandlestickPlaceholder({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.candles(symbol), queryFn: () => heliumApi.candles(symbol) });
  const candles = query.data ?? [];
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Candles</h2>
      <div className="flex h-56 items-end gap-2 border-b border-l border-slate-700 px-3 pb-3">
        {candles.map((candle) => {
          const height = Math.max(24, Math.min(180, Number(candle.volume) * 7));
          return (
            <div className="flex flex-1 flex-col items-center gap-2" key={candle.time}>
              <div className="w-full rounded-t bg-cyan-400/70" style={{ height }} />
              <span className="text-[10px] text-slate-500">{candle.time}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function BookSide({ title, rows, tone }: Readonly<{ title: string; rows: { price: string; quantity: string; total: string }[]; tone: string }>) {
  return (
    <div>
      <p className="mb-2 text-sm font-medium text-slate-400">{title}</p>
      <div className="space-y-1 text-sm">
        {rows.map((row) => (
          <div className="grid grid-cols-3 gap-2" key={`${title}-${row.price}`}>
            <span className={tone}>{row.price}</span>
            <span>{row.quantity}</span>
            <span className="text-slate-400">{row.total}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
