"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils/cn";
import { formatAmount, shortTime } from "@/lib/utils/format";
import type { Balance, CandleResponse, PublicTrade, TickerResponse, WithdrawalRecord } from "@/lib/api/types";

export function PriceChangeBadge({ openPrice, lastPrice }: Readonly<{ openPrice: number; lastPrice: number }>) {
  if (!openPrice) {
    return <Badge tone="info">—</Badge>;
  }
  const changePct = ((lastPrice - openPrice) / openPrice) * 100;
  const negative = changePct < 0;
  return <Badge tone={negative ? "danger" : "success"}>{`${negative ? "" : "+"}${changePct.toFixed(2)}%`}</Badge>;
}

export function MarketTicker({ tickers }: Readonly<{ tickers: TickerResponse[] }>) {
  const items = tickers.length ? [...tickers, ...tickers] : [];
  if (!items.length) {
    return null;
  }
  return (
    <div className="glass-panel overflow-hidden rounded-lg" aria-label="Live market ticker">
      <div className="flex min-w-max animate-ticker gap-8 px-4 py-3">
        {items.map((ticker, index) => (
          <div className="flex items-center gap-3 text-sm" key={`${ticker.market}-${index}`}>
            <span className="font-semibold text-foreground">{ticker.market}</span>
            <span className="font-mono text-slate-200">{formatAmount(ticker.lastPrice)}</span>
            <PriceChangeBadge lastPrice={ticker.lastPrice} openPrice={ticker.openPrice24h} />
          </div>
        ))}
      </div>
    </div>
  );
}

export function AssetCard({ balance }: Readonly<{ balance: Balance }>) {
  return (
    <Card className="animate-fade-up">
      <CardContent>
        <div className="flex items-start justify-between gap-3">
          <p className="text-title-lg">{balance.asset}</p>
          <Badge tone={balance.locked > 0 ? "warning" : "success"}>{balance.locked > 0 ? "Partially locked" : "Available"}</Badge>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-3 text-sm">
          <Metric label="Available" value={formatAmount(balance.available)} />
          <Metric label="Locked" value={formatAmount(balance.locked)} />
        </div>
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

export function CandlestickChart({ candles }: Readonly<{ candles: CandleResponse[] }>) {
  const highs = candles.map((candle) => candle.high);
  const lows = candles.map((candle) => candle.low);
  const high = Math.max(...highs, 1);
  const low = Math.min(...lows, 0);
  const range = Math.max(high - low, Number.EPSILON);
  return (
    <Card className="terminal-grid">
      <CardHeader>
        <CardTitle>Candles</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex h-72 items-end gap-2 overflow-hidden px-2 pb-2">
          {candles.map((candle) => {
            const up = candle.close >= candle.open;
            const wickHeight = Math.max(18, ((candle.high - candle.low) / range) * 240);
            const bodyHeight = Math.max(8, (Math.abs(candle.close - candle.open) / range) * 240);
            return (
              <div className="flex min-w-5 flex-1 flex-col items-center justify-end" key={candle.openTime}>
                <div className="relative flex justify-center" style={{ height: wickHeight }}>
                  <span className={cn("absolute h-full w-px", up ? "bg-emerald-300/80" : "bg-red-300/80")} />
                  <span className={cn("absolute bottom-1/2 w-3 rounded-sm", up ? "bg-emerald-300" : "bg-red-300")} style={{ height: bodyHeight }} />
                </div>
                <span className="mt-2 text-micro text-muted-foreground">{shortTime(candle.openTime)}</span>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

export type BookLevel = {
  price: number;
  quantity: number;
  total: number;
};

export function OrderBookPanel({ bids, asks }: Readonly<{ bids: BookLevel[]; asks: BookLevel[] }>) {
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

export function RecentTradesPanel({ trades }: Readonly<{ trades: PublicTrade[] }>) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent Trades</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {trades.map((trade) => (
          <div className="grid grid-cols-[1fr_1fr_88px] gap-2 rounded-sm px-2 py-1.5 text-xs hover:bg-white/[0.035]" key={trade.executionId}>
            <span className="font-mono text-slate-200">{formatAmount(trade.price)}</span>
            <span className="font-mono text-slate-300">{formatAmount(trade.quantity)}</span>
            <span className="text-right text-muted-foreground">{shortTime(trade.tradedAt)}</span>
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
            <p className="font-semibold text-foreground">{item.asset} withdrawal {String(item.status).toLowerCase()}</p>
            <p className="mt-1 text-xs text-muted-foreground">{formatAmount(item.amount)} on {item.network}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function BookSide({ title, rows, tone }: Readonly<{ title: string; rows: BookLevel[]; tone: "bid" | "ask" }>) {
  const max = Math.max(...rows.map((row) => row.total), Number.EPSILON);
  return (
    <div>
      <p className="mb-2 text-micro font-semibold uppercase text-muted-foreground">{title}</p>
      <div className="space-y-1">
        {rows.map((row) => (
          <div className="relative grid grid-cols-3 gap-2 overflow-hidden rounded-sm px-2 py-1.5 text-xs" key={`${title}-${row.price}`}>
            <span
              aria-hidden
              className={cn("absolute inset-y-0 right-0", tone === "bid" ? "bg-emerald-400/8" : "bg-red-400/8")}
              style={{ width: `${Math.min(100, (row.total / max) * 100)}%` }}
            />
            <span className={cn("relative font-mono", tone === "bid" ? "text-emerald-300" : "text-red-300")}>{formatAmount(row.price)}</span>
            <span className="relative font-mono text-slate-200">{formatAmount(row.quantity)}</span>
            <span className="relative text-right font-mono text-muted-foreground">{formatAmount(row.total)}</span>
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
