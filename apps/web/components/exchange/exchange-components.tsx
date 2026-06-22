"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils/cn";
import type { AssetBalance, CandlePoint, MarketSummary, OrderBookLevel, PublicTrade, WithdrawalRecord } from "@/lib/api/types";

export function PriceChangeBadge({ value }: Readonly<{ value: string }>) {
  const negative = value.trim().startsWith("-");
  return <Badge tone={negative ? "danger" : "success"}>{negative ? value : `+${value.replace(/^\+/, "")}`}</Badge>;
}

export function MarketTicker({ markets }: Readonly<{ markets: MarketSummary[] }>) {
  const items = markets.length ? [...markets, ...markets] : [];
  return (
    <div className="glass-panel overflow-hidden rounded-lg" aria-label="Live market ticker">
      <div className="flex min-w-max animate-ticker gap-8 px-4 py-3">
        {items.map((market, index) => (
          <div className="flex items-center gap-3 text-sm" key={`${market.symbol}-${index}`}>
            <span className="font-semibold text-foreground">{market.symbol}</span>
            <span className="font-mono text-slate-200">{market.lastPrice}</span>
            <PriceChangeBadge value={market.change24h} />
          </div>
        ))}
      </div>
    </div>
  );
}

export function AssetCard({ asset }: Readonly<{ asset: AssetBalance }>) {
  return (
    <Card className="animate-fade-up">
      <CardContent>
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-title-lg">{asset.asset}</p>
            <p className="text-xs text-muted-foreground">{asset.name}</p>
          </div>
          <Badge tone={asset.withdrawalEnabled ? "success" : "warning"}>{asset.withdrawalEnabled ? "Live" : "Limited"}</Badge>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-3 text-sm">
          <Metric label="Available" value={asset.available} />
          <Metric label="Locked" value={asset.locked} />
        </div>
        <p className="mt-5 font-mono text-xl text-foreground">{asset.totalUsd}</p>
      </CardContent>
    </Card>
  );
}

export function WalletBalanceCard({ label, value, detail }: Readonly<{ label: string; value: string; detail?: string }>) {
  return (
    <Card>
      <CardContent>
        <p className="text-micro font-semibold uppercase text-muted-foreground">{label}</p>
        <p className="mt-2 font-mono text-2xl font-semibold text-foreground">{value}</p>
        {detail ? <p className="mt-2 text-xs text-muted-foreground">{detail}</p> : null}
      </CardContent>
    </Card>
  );
}

export function PortfolioChart({ values }: Readonly<{ values: number[] }>) {
  const max = Math.max(...values, 1);
  const points = values.map((value, index) => `${(index / Math.max(values.length - 1, 1)) * 100},${100 - (value / max) * 78}`).join(" ");
  return (
    <Card className="terminal-grid">
      <CardHeader>
        <CardTitle>Portfolio Curve</CardTitle>
      </CardHeader>
      <CardContent>
        <svg aria-label="Portfolio chart" className="h-40 w-full" role="img" viewBox="0 0 100 100" preserveAspectRatio="none">
          <polyline fill="none" points={points} stroke="hsl(var(--primary))" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" />
          <polygon fill="url(#portfolioGradient)" points={`0,100 ${points} 100,100`} opacity="0.38" />
          <defs>
            <linearGradient id="portfolioGradient" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="hsl(var(--primary))" />
              <stop offset="100%" stopColor="transparent" />
            </linearGradient>
          </defs>
        </svg>
      </CardContent>
    </Card>
  );
}

export function CandlestickChart({ candles }: Readonly<{ candles: CandlePoint[] }>) {
  const highs = candles.map((candle) => Number(candle.high));
  const lows = candles.map((candle) => Number(candle.low));
  const high = Math.max(...highs, 1);
  const low = Math.min(...lows, 0);
  const range = Math.max(high - low, 1);
  return (
    <Card className="terminal-grid">
      <CardHeader>
        <CardTitle>Candlestick Chart</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex h-72 items-end gap-2 overflow-hidden px-2 pb-2">
          {candles.map((candle) => {
            const open = Number(candle.open);
            const close = Number(candle.close);
            const candleHigh = Number(candle.high);
            const candleLow = Number(candle.low);
            const up = close >= open;
            const wickHeight = Math.max(18, ((candleHigh - candleLow) / range) * 240);
            const bodyHeight = Math.max(8, (Math.abs(close - open) / range) * 240);
            return (
              <div className="flex min-w-5 flex-1 flex-col items-center justify-end" key={candle.time}>
                <div className="relative flex justify-center" style={{ height: wickHeight }}>
                  <span className={cn("absolute h-full w-px", up ? "bg-emerald-300/80" : "bg-red-300/80")} />
                  <span className={cn("absolute bottom-1/2 w-3 rounded-sm", up ? "bg-emerald-300" : "bg-red-300")} style={{ height: bodyHeight }} />
                </div>
                <span className="mt-2 text-micro text-muted-foreground">{candle.time}</span>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

export function OrderBook({
  bids,
  asks
}: Readonly<{ bids: OrderBookLevel[]; asks: OrderBookLevel[] }>) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Order Book</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid gap-4 md:grid-cols-2">
          <BookSide title="Bids" rows={bids} tone="bid" />
          <BookSide title="Asks" rows={asks} tone="ask" />
        </div>
      </CardContent>
    </Card>
  );
}

export function RecentTrades({ trades }: Readonly<{ trades: PublicTrade[] }>) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent Trades</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {trades.map((trade) => (
          <div className="grid grid-cols-[1fr_1fr_56px_72px] gap-2 rounded-sm px-2 py-1.5 text-xs hover:bg-white/[0.035]" key={trade.id}>
            <span className={trade.side === "BUY" ? "font-mono text-emerald-300" : "font-mono text-red-300"}>{trade.price}</span>
            <span className="font-mono text-slate-200">{trade.quantity}</span>
            <Badge tone={trade.side === "BUY" ? "success" : "danger"}>{trade.side}</Badge>
            <span className="text-right text-muted-foreground">{trade.time}</span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

export function TransactionTimeline({ items }: Readonly<{ items: WithdrawalRecord[] }>) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Transaction Timeline</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {items.map((item) => (
          <div className="relative pl-6 text-sm" key={item.id}>
            <span className="absolute left-0 top-1.5 h-2.5 w-2.5 rounded-full bg-primary shadow-glow-cyan" />
            <p className="font-semibold text-foreground">{item.asset} withdrawal {item.status.toLowerCase()}</p>
            <p className="mt-1 text-xs text-muted-foreground">{item.amount} on {item.network}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function BookSide({ title, rows, tone }: Readonly<{ title: string; rows: OrderBookLevel[]; tone: "bid" | "ask" }>) {
  const max = Math.max(...rows.map((row) => Number(row.total)), 1);
  return (
    <div>
      <p className="mb-2 text-micro font-semibold uppercase text-muted-foreground">{title}</p>
      <div className="space-y-1">
        {rows.map((row) => (
          <div className="relative grid grid-cols-3 gap-2 overflow-hidden rounded-sm px-2 py-1.5 text-xs" key={`${title}-${row.price}`}>
            <span
              aria-hidden
              className={cn("absolute inset-y-0 right-0", tone === "bid" ? "bg-emerald-400/8" : "bg-red-400/8")}
              style={{ width: `${Math.min(100, (Number(row.total) / max) * 100)}%` }}
            />
            <span className={cn("relative font-mono", tone === "bid" ? "text-emerald-300" : "text-red-300")}>{row.price}</span>
            <span className="relative font-mono text-slate-200">{row.quantity}</span>
            <span className="relative text-right font-mono text-muted-foreground">{row.total}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Metric({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-md border border-border/70 bg-black/18 p-3">
      <p className="text-micro font-semibold uppercase text-muted-foreground">{label}</p>
      <p className="mt-1 font-mono text-sm text-foreground">{value}</p>
    </div>
  );
}
