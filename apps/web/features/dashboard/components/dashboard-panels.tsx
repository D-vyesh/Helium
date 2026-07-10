"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Search } from "@/components/ui/search";
import { heliumApi } from "@/lib/api/client";
import type { ActivityItem, DashboardMarketCard, DashboardResponse, PortfolioAsset, WithdrawalRecord } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { cn } from "@/lib/utils/cn";
import { formatAmount, shortDate } from "@/lib/utils/format";
import { useMarketStream } from "@/lib/ws/market-stream";
import { useTradingStream } from "@/lib/ws/trading-stream";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";

const PENDING_WITHDRAWAL_STATUSES = new Set(["REQUESTED", "APPROVED", "BROADCASTED"]);
const PENDING_DEPOSIT_STATUSES = new Set(["DETECTED", "CONFIRMED"]);

export function ExchangeDashboard() {
  const queryClient = useQueryClient();
  const dashboardQuery = useQuery({ queryKey: queryKeys.dashboard, queryFn: heliumApi.dashboard });
  const ordersQuery = useQuery({ queryKey: queryKeys.openOrders, queryFn: heliumApi.openOrders });
  const tradesQuery = useQuery({ queryKey: queryKeys.trades, queryFn: heliumApi.tradeHistory });
  const depositsQuery = useQuery({ queryKey: queryKeys.deposits, queryFn: heliumApi.deposits });
  const withdrawalsQuery = useQuery({ queryKey: queryKeys.withdrawals, queryFn: heliumApi.withdrawals });

  const refreshDashboard = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
  }, [queryClient]);

  useTradingStream((event) => {
    if (event.type === "heartbeat" || event.type === "connected") return;
    refreshDashboard();
    void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
    void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
    void queryClient.invalidateQueries({ queryKey: queryKeys.trades });
    void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
  });

  if (dashboardQuery.isLoading) return <LoadingState label="Loading dashboard" />;
  if (dashboardQuery.isError) {
    return <ErrorState title="Could not load dashboard" error={dashboardQuery.error} onRetry={() => void dashboardQuery.refetch()} />;
  }
  const dashboard = dashboardQuery.data;
  if (!dashboard) return <EmptyState title="Dashboard unavailable" />;

  const pendingDeposits = (depositsQuery.data ?? []).filter((deposit) => PENDING_DEPOSIT_STATUSES.has(String(deposit.status)));
  const pendingWithdrawals = (withdrawalsQuery.data ?? []).filter((withdrawal) => PENDING_WITHDRAWAL_STATUSES.has(String(withdrawal.status)));

  return (
    <div className="space-y-6">
      <DashboardRealtimeBridge onMarketEvent={refreshDashboard} symbols={dashboard.markets.map((market) => market.marketSymbol)} />
      <PortfolioHero dashboard={dashboard} />
      <section className="grid gap-4 xl:grid-cols-[1.4fr_0.9fr]">
        <PortfolioAssets assets={dashboard.portfolio.assets} />
        <AllocationPanel assets={dashboard.portfolio.assets} />
      </section>
      <section className="grid gap-4 xl:grid-cols-[1fr_1fr]">
        <MarketOverview dashboard={dashboard} />
        <WatchlistPanel dashboard={dashboard} />
      </section>
      <section className="grid gap-4 xl:grid-cols-[1.25fr_0.75fr]">
        <Card>
          <CardHeader><CardTitle>Open Orders</CardTitle></CardHeader>
          <CardContent>
            {ordersQuery.isLoading ? <LoadingState label="Loading open orders" /> : null}
            {ordersQuery.isError ? <ErrorState title="Could not load open orders" error={ordersQuery.error} onRetry={() => void ordersQuery.refetch()} /> : null}
            {ordersQuery.data && !ordersQuery.data.length ? <EmptyState title="No open orders" /> : null}
            {ordersQuery.data?.length ? (
              <DataTable
                columns={["Market", "Side", "Price", "Quantity", "Filled", "Status"]}
                rows={ordersQuery.data.map((order) => [
                  order.marketSymbol,
                  order.side,
                  formatAmount(order.limitPrice),
                  formatAmount(order.quantity),
                  formatAmount(order.filledQuantity),
                  <Badge key={order.id} tone={order.status === "OPEN" ? "success" : "warning"}>{order.status}</Badge>
                ])}
              />
            ) : null}
          </CardContent>
        </Card>
        <ActivityCenter activity={dashboard.activity} />
      </section>
      <section className="grid gap-4 xl:grid-cols-3">
        <RecentTradesPanel query={tradesQuery} />
        <PendingDepositsPanel rows={pendingDeposits} loading={depositsQuery.isLoading} error={depositsQuery.error} onRetry={() => void depositsQuery.refetch()} />
        <PendingWithdrawalsPanel rows={pendingWithdrawals} loading={withdrawalsQuery.isLoading} error={withdrawalsQuery.error} onRetry={() => void withdrawalsQuery.refetch()} />
      </section>
    </div>
  );
}

function PortfolioHero({ dashboard }: Readonly<{ dashboard: DashboardResponse }>) {
  const status = dashboard.exchangeStatus;
  return (
    <section className="grid gap-4 lg:grid-cols-[1.3fr_0.7fr]">
      <Card className="overflow-hidden">
        <CardContent className="relative p-5">
          <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-cyan-300/70 via-emerald-300/50 to-amber-300/70" />
          <p className="text-micro font-semibold uppercase text-muted-foreground">Portfolio Total</p>
          <div className="mt-2 flex flex-wrap items-end gap-4">
            <p className="font-mono text-4xl font-semibold text-foreground">${formatAmount(dashboard.portfolio.totalValue, 2)}</p>
            <ChangePill value={dashboard.portfolio.dailyChangePercent} />
          </div>
          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <Metric label="Daily Change" value={`$${formatAmount(dashboard.portfolio.dailyChange, 2)}`} />
            <Metric label="Assets" value={String(dashboard.portfolio.assetCount)} />
            <Metric label="Active Markets" value={String(status.activeMarkets)} />
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-foreground">Exchange Status</p>
            <Badge tone={status.connected ? "success" : "warning"}>{status.connected ? "Live" : "Syncing"}</Badge>
          </div>
          <Metric label="Source" value={status.source} />
          <Metric label="Last Synchronization" value={status.lastSynchronization ? shortDate(status.lastSynchronization) : "—"} />
          <div className="grid grid-cols-2 gap-3">
            <Metric label="Reconnects" value={String(status.reconnects)} />
            <Metric label="Dropped" value={String(status.droppedMessages)} />
          </div>
        </CardContent>
      </Card>
    </section>
  );
}

function PortfolioAssets({ assets }: Readonly<{ assets: PortfolioAsset[] }>) {
  if (!assets.length) return <EmptyState title="No portfolio assets" />;
  return (
    <Card>
      <CardHeader><CardTitle>Portfolio</CardTitle></CardHeader>
      <CardContent>
        <DataTable
          columns={["Asset", "Available", "Locked", "Total", "Price", "Value", "Allocation", "Avg", "PnL"]}
          rows={assets.map((asset) => [
            <span className="font-semibold text-cyan-100" key={asset.asset}>{asset.asset}</span>,
            formatAmount(asset.available),
            formatAmount(asset.locked),
            formatAmount(asset.total),
            asset.currentPrice === null ? "—" : `$${formatAmount(asset.currentPrice, 2)}`,
            asset.marketValue === null ? "—" : `$${formatAmount(asset.marketValue, 2)}`,
            `${formatAmount(asset.allocationPercent, 2)}%`,
            asset.averageAcquisitionPrice === null ? "—" : `$${formatAmount(asset.averageAcquisitionPrice, 2)}`,
            <PnlValue key={`${asset.asset}-pnl`} value={asset.unrealizedPnl} />
          ])}
        />
      </CardContent>
    </Card>
  );
}

function AllocationPanel({ assets }: Readonly<{ assets: PortfolioAsset[] }>) {
  const visible = assets.filter((asset) => asset.allocationPercent > 0);
  return (
    <Card>
      <CardHeader><CardTitle>Allocation</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        {!visible.length ? <EmptyState title="No priced assets" /> : null}
        {visible.length ? (
          <>
            <div className="flex h-3 overflow-hidden rounded-sm bg-white/8">
              {visible.map((asset, index) => (
                <span
                  aria-label={`${asset.asset} ${formatAmount(asset.allocationPercent, 2)}%`}
                  className={cn("h-full", allocationColor(index))}
                  key={asset.asset}
                  style={{ width: `${Math.max(1, asset.allocationPercent)}%` }}
                />
              ))}
            </div>
            <div className="space-y-3">
              {visible.map((asset, index) => (
                <div className="grid grid-cols-[12px_1fr_auto] items-center gap-3 text-sm" key={asset.asset}>
                  <span className={cn("h-3 rounded-sm", allocationColor(index))} />
                  <span className="font-semibold text-foreground">{asset.asset}</span>
                  <span className="font-mono text-muted-foreground">{formatAmount(asset.allocationPercent, 2)}%</span>
                </div>
              ))}
            </div>
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}

function MarketOverview({ dashboard }: Readonly<{ dashboard: DashboardResponse }>) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Market Overview</CardTitle>
        <Badge tone={dashboard.exchangeStatus.connected ? "success" : "warning"}>{dashboard.exchangeStatus.connected ? "Live" : "Syncing"}</Badge>
      </CardHeader>
      <CardContent className="grid gap-3">
        {dashboard.markets.map((market) => <MarketCard market={market} key={market.marketSymbol} />)}
      </CardContent>
    </Card>
  );
}

function WatchlistPanel({ dashboard }: Readonly<{ dashboard: DashboardResponse }>) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const watchlistSymbols = useMemo(() => new Set(dashboard.watchlist.map((item) => item.marketSymbol)), [dashboard.watchlist]);
  const candidates = dashboard.markets.filter((market) => market.marketSymbol.toLowerCase().includes(search.toLowerCase()));
  const mutation = useMutation({
    mutationFn: heliumApi.upsertWatchlistItem,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist });
    }
  });
  const remove = useMutation({
    mutationFn: heliumApi.removeWatchlistItem,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist });
    }
  });

  return (
    <Card>
      <CardHeader><CardTitle>Watchlist</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <Search onChange={(event) => setSearch(event.target.value)} placeholder="Search markets" value={search} />
        <div className="space-y-2">
          {dashboard.watchlist.map((item, index) => item.market ? (
            <div className="grid grid-cols-[1fr_auto_auto] items-center gap-2 rounded-md border border-border/70 bg-black/18 p-3" key={item.marketSymbol}>
              <Link className="font-semibold text-cyan-100 hover:text-cyan-50" href={`/trade?symbol=${item.marketSymbol}`}>{item.marketSymbol}</Link>
              <Button
                disabled={mutation.isPending}
                onClick={() => mutation.mutate({ marketSymbol: item.marketSymbol, pinned: !item.pinned, sortOrder: index })}
                size="sm"
                type="button"
                variant="secondary"
              >
                {item.pinned ? "Pinned" : "Pin"}
              </Button>
              <Button disabled={remove.isPending} onClick={() => remove.mutate(item.marketSymbol)} size="sm" type="button" variant="ghost">Remove</Button>
            </div>
          ) : null)}
        </div>
        {search ? (
          <div className="space-y-2 border-t border-border/70 pt-3">
            {candidates.map((market, index) => (
              <div className="grid grid-cols-[1fr_auto] items-center gap-2 text-sm" key={market.marketSymbol}>
                <span className="font-semibold">{market.marketSymbol}</span>
                <Button
                  disabled={mutation.isPending || watchlistSymbols.has(market.marketSymbol)}
                  onClick={() => mutation.mutate({ marketSymbol: market.marketSymbol, pinned: false, sortOrder: dashboard.watchlist.length + index })}
                  size="sm"
                  type="button"
                  variant="secondary"
                >
                  {watchlistSymbols.has(market.marketSymbol) ? "Added" : "Add"}
                </Button>
              </div>
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ActivityCenter({ activity }: Readonly<{ activity: ActivityItem[] }>) {
  return (
    <Card>
      <CardHeader><CardTitle>Activity</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        {!activity.length ? <EmptyState title="No recent activity" /> : null}
        {activity.slice(0, 12).map((item) => (
          <div className="grid grid-cols-[10px_1fr] gap-3 text-sm" key={item.id}>
            <span className={cn("mt-1.5 h-2.5 rounded-full", activityTone(item.category))} />
            <div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-semibold text-foreground">{humanizeEvent(item.eventType)}</p>
                <span className="text-xs text-muted-foreground">{shortDate(item.occurredAt)}</span>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{item.summary}</p>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function RecentTradesPanel({ query }: Readonly<{ query: ReturnType<typeof useQuery<unknown>> }>) {
  const data = query.data as Awaited<ReturnType<typeof heliumApi.tradeHistory>> | undefined;
  return (
    <Card>
      <CardHeader><CardTitle>Recent Trades</CardTitle></CardHeader>
      <CardContent>
        {query.isLoading ? <LoadingState label="Loading recent trades" /> : null}
        {query.isError ? <ErrorState title="Could not load recent trades" error={query.error} onRetry={() => void query.refetch()} /> : null}
        {data && !data.length ? <EmptyState title="No recent trades" /> : null}
        {data?.length ? (
          <DataTable
            columns={["Market", "Side", "Price", "Qty", "Time"]}
            rows={data.slice(0, 8).map((trade) => [
              trade.market,
              trade.side,
              formatAmount(trade.price),
              formatAmount(trade.quantity),
              shortDate(trade.time)
            ])}
          />
        ) : null}
      </CardContent>
    </Card>
  );
}

function PendingDepositsPanel({
  rows,
  loading,
  error,
  onRetry
}: Readonly<{ rows: { id: string; asset: string; amount: number; network: string; status: string }[]; loading: boolean; error: unknown; onRetry: () => void }>) {
  return (
    <Card>
      <CardHeader><CardTitle>Pending Deposits</CardTitle></CardHeader>
      <CardContent>
        {loading ? <LoadingState label="Loading deposits" /> : null}
        {error ? <ErrorState title="Could not load deposits" error={error} onRetry={onRetry} /> : null}
        {!loading && !error && !rows.length ? <EmptyState title="No pending deposits" /> : null}
        {rows.length ? (
          <DataTable
            columns={["Asset", "Amount", "Network", "Status"]}
            rows={rows.map((deposit) => [deposit.asset, formatAmount(deposit.amount), deposit.network, deposit.status])}
          />
        ) : null}
      </CardContent>
    </Card>
  );
}

function PendingWithdrawalsPanel({
  rows,
  loading,
  error,
  onRetry
}: Readonly<{ rows: WithdrawalRecord[]; loading: boolean; error: unknown; onRetry: () => void }>) {
  return (
    <Card>
      <CardHeader><CardTitle>Pending Withdrawals</CardTitle></CardHeader>
      <CardContent>
        {loading ? <LoadingState label="Loading withdrawals" /> : null}
        {error ? <ErrorState title="Could not load withdrawals" error={error} onRetry={onRetry} /> : null}
        {!loading && !error && !rows.length ? <EmptyState title="No pending withdrawals" /> : null}
        {rows.length ? (
          <DataTable
            columns={["Asset", "Amount", "Network", "Status"]}
            rows={rows.map((withdrawal) => [withdrawal.asset, formatAmount(withdrawal.amount), withdrawal.network, withdrawal.status])}
          />
        ) : null}
      </CardContent>
    </Card>
  );
}

function MarketCard({ market }: Readonly<{ market: DashboardMarketCard }>) {
  return (
    <Link
      className="group grid gap-3 rounded-md border border-border/70 bg-black/18 p-3 transition hover:border-cyan-300/35 hover:bg-white/8"
      href={`/trade?symbol=${market.marketSymbol}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-foreground">{market.marketSymbol}</p>
          <p className="mt-1 font-mono text-xl text-slate-100">{market.currentPrice === null ? "—" : formatAmount(market.currentPrice, 2)}</p>
        </div>
        <ChangePill value={market.priceChangePercent24h} />
      </div>
      <Sparkline values={market.miniChart} />
      <div className="grid grid-cols-3 gap-2 text-xs text-muted-foreground">
        <span>High <b className="font-mono text-slate-300">{formatAmount(market.highPrice24h, 2)}</b></span>
        <span>Low <b className="font-mono text-slate-300">{formatAmount(market.lowPrice24h, 2)}</b></span>
        <span>Vol <b className="font-mono text-slate-300">{formatAmount(market.volume24h, 2)}</b></span>
        <span>Bid <b className="font-mono text-emerald-300">{formatAmount(market.bestBid, 2)}</b></span>
        <span>Ask <b className="font-mono text-red-300">{formatAmount(market.bestAsk, 2)}</b></span>
        <span>Spread <b className="font-mono text-amber-200">{formatAmount(market.spread, 4)}</b></span>
      </div>
    </Link>
  );
}

function DashboardRealtimeBridge({ symbols, onMarketEvent }: Readonly<{ symbols: string[]; onMarketEvent: () => void }>) {
  return (
    <>
      {symbols.map((symbol) => <MarketStreamBridge key={symbol} onMarketEvent={onMarketEvent} symbol={symbol} />)}
    </>
  );
}

function MarketStreamBridge({ symbol, onMarketEvent }: Readonly<{ symbol: string; onMarketEvent: () => void }>) {
  useMarketStream(symbol, "ticker", (event) => {
    if (event.type === "ticker") onMarketEvent();
  });
  return null;
}

function Sparkline({ values }: Readonly<{ values: number[] }>) {
  if (values.length < 2) return <div className="h-12 rounded-sm bg-white/6" />;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = Math.max(max - min, Number.EPSILON);
  return (
    <div className="flex h-12 items-end gap-0.5 overflow-hidden rounded-sm bg-white/[0.035] px-1 py-1">
      {values.slice(-36).map((value, index) => (
        <span
          className="flex-1 rounded-t-sm bg-cyan-300/45 transition group-hover:bg-cyan-200/70"
          key={`${value}-${index}`}
          style={{ height: `${18 + ((value - min) / range) * 82}%` }}
        />
      ))}
    </div>
  );
}

function ChangePill({ value }: Readonly<{ value: number | null }>) {
  if (value === null) return <Badge tone="info">—</Badge>;
  return <Badge tone={value < 0 ? "danger" : "success"}>{`${value < 0 ? "" : "+"}${formatAmount(value, 2)}%`}</Badge>;
}

function PnlValue({ value }: Readonly<{ value: number | null }>) {
  if (value === null) return <span>—</span>;
  return <span className={cn("font-mono", value < 0 ? "text-red-300" : "text-emerald-300")}>{value < 0 ? "" : "+"}${formatAmount(value, 2)}</span>;
}

function Metric({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-md border border-border/70 bg-black/18 p-3">
      <p className="text-micro font-semibold uppercase text-muted-foreground">{label}</p>
      <p className="mt-1 font-mono text-sm text-foreground">{value}</p>
    </div>
  );
}

function humanizeEvent(value: string) {
  return value.replaceAll("_", " ").replaceAll(".", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function activityTone(category: string) {
  if (category === "TRADING") return "bg-cyan-300 shadow-glow-cyan";
  if (category === "WALLET") return "bg-emerald-300";
  return "bg-amber-300";
}

function allocationColor(index: number) {
  return ["bg-cyan-300", "bg-emerald-300", "bg-amber-300", "bg-fuchsia-300", "bg-slate-300"][index % 5];
}
