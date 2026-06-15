"use client";

import { PageHeader } from "@/components/layout/app-shell";
import { CandlestickPlaceholder, MarketSelector, OrderBook, RecentTrades } from "@/features/market-data/components/market-data-panels";
import { BalancesPanel, OpenOrders, OrderEntryForm, PositionSummary } from "./trading-panels";
import { useSearchParams } from "next/navigation";

export function TradingTerminal() {
  const params = useSearchParams();
  const market = params.get("symbol") ?? "BTC-USD";

  return (
    <>
      <PageHeader title="Trading Terminal" detail="Limit-order spot trading workspace backed by Trading, Matching, Ledger, Wallet, and Market Data projections." />
      <div className="mb-4">
        <MarketSelector selected={market} />
      </div>
      <div className="grid gap-4 xl:grid-cols-[280px_1fr_360px]">
        <div className="space-y-4">
          <OrderEntryForm market={market} />
          <PositionSummary market={market} />
        </div>
        <div className="space-y-4">
          <CandlestickPlaceholder symbol={market} />
          <OrderBook symbol={market} />
          <section className="rounded border border-slate-800 bg-slate-900 p-4">
            <h2 className="mb-3 text-lg font-semibold">Open Orders</h2>
            <OpenOrders />
          </section>
        </div>
        <div className="space-y-4">
          <RecentTrades symbol={market} />
          <BalancesPanel />
        </div>
      </div>
    </>
  );
}
