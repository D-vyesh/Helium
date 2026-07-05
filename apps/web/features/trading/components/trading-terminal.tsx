"use client";

import { PageHeader } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CandleChart, MarketSelector, OrderBook, RecentTrades, TickerHeader } from "@/features/market-data/components/market-data-panels";
import { BalancesPanel, OpenOrders, OrderEntryForm, PositionSummary } from "./trading-panels";
import { useSearchParams } from "next/navigation";

export function TradingTerminal() {
  const params = useSearchParams();
  const market = params.get("symbol") ?? "BTC-USD";

  return (
    <>
      <PageHeader title="Trading Terminal" detail="Limit-order spot trading backed by the live Trading, Matching, Ledger, and Market Data services." />
      <div className="mb-4 space-y-3">
        <MarketSelector selected={market} />
        <TickerHeader symbol={market} />
      </div>
      <div className="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)_380px]">
        <div className="space-y-4">
          <OrderEntryForm market={market} />
          <PositionSummary />
        </div>
        <div className="space-y-4">
          <CandleChart symbol={market} />
          <OrderBook symbol={market} />
          <Card>
            <CardHeader>
              <CardTitle>Open Orders</CardTitle>
            </CardHeader>
            <CardContent>
              <OpenOrders />
            </CardContent>
          </Card>
        </div>
        <div className="space-y-4">
          <RecentTrades symbol={market} />
          <BalancesPanel />
        </div>
      </div>
    </>
  );
}
