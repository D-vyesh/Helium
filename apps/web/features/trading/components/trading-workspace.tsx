"use client";

import { CandlestickChart } from "@/components/charts/candlestick-chart";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { DataTable } from "@/components/ui/table";
import { Search } from "@/components/ui/search";
import { Toast } from "@/components/ui/toast";
import { heliumApi } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { ActivityItem, BookOrder, CandleResponse, DashboardMarketCard, OrderBookView } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { cn } from "@/lib/utils/cn";
import { formatAmount, shortDate, shortTime } from "@/lib/utils/format";
import { useMarketStream } from "@/lib/ws/market-stream";
import { useTradingStream } from "@/lib/ws/trading-stream";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { OrderEntryForm, PositionSummary } from "./trading-panels";

const TIMEFRAMES = ["1m", "5m", "15m", "30m", "1H", "4H", "1D", "1W", "1M"];
const INDICATORS = ["EMA", "SMA", "MACD", "RSI", "Bollinger"];
const DRAWING_TOOLS = ["Trendline", "Fib", "Horizontal", "Crosshair"];
const LOWER_TABS = ["Open Orders", "Order History", "Trade History", "Portfolio", "Activity"] as const;

type LowerTab = typeof LOWER_TABS[number];
type MarketCategory = "all" | "favorites" | "pinned" | "spot";

export function TradingWorkspace({ symbol }: Readonly<{ symbol: string }>) {
  const queryClient = useQueryClient();
  const sessionQuery = useQuery({ queryKey: queryKeys.session, queryFn: heliumApi.session });
  const layoutOwner = sessionQuery.data?.id ?? "local";
  const [leftCollapsed, setLeftCollapsed] = usePersistedFlag(`helium.trade.${layoutOwner}.leftCollapsed`, false);
  const [rightCollapsed, setRightCollapsed] = usePersistedFlag(`helium.trade.${layoutOwner}.rightCollapsed`, false);
  const [bottomCollapsed, setBottomCollapsed] = usePersistedFlag(`helium.trade.${layoutOwner}.bottomCollapsed`, false);

  const handleTradingEvent = useCallback((event: { type: string }) => {
    if (event.type !== "order") return;
    void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
    void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
    void queryClient.invalidateQueries({ queryKey: queryKeys.trades });
    void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardPortfolio });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardActivity });
  }, [queryClient]);

  return (
    <div className="space-y-3">
      <TradingStreamBridge onEvent={handleTradingEvent} />
      <WorkspaceTicker symbol={symbol} />
      <div className={cn("grid gap-3", leftCollapsed ? "xl:grid-cols-[44px_minmax(0,1fr)_390px]" : "xl:grid-cols-[280px_minmax(0,1fr)_390px]", rightCollapsed && "xl:grid-cols-[280px_minmax(0,1fr)_44px]", leftCollapsed && rightCollapsed && "xl:grid-cols-[44px_minmax(0,1fr)_44px]")}>
        <WorkspacePanel collapsed={leftCollapsed} label="Markets" onToggle={() => setLeftCollapsed(!leftCollapsed)}>
          <MarketWorkspaceSidebar selected={symbol} />
        </WorkspacePanel>
        <main className="min-w-0 space-y-3">
          <AdvancedChartPanel symbol={symbol} />
          {!bottomCollapsed ? <LowerWorkspacePanel /> : null}
          <Button className="w-full xl:hidden" onClick={() => setBottomCollapsed(!bottomCollapsed)} type="button" variant="secondary">
            {bottomCollapsed ? "Show lower panel" : "Hide lower panel"}
          </Button>
        </main>
        <WorkspacePanel collapsed={rightCollapsed} label="Trade" onToggle={() => setRightCollapsed(!rightCollapsed)}>
          <div className="space-y-3">
            <OrderEntryForm market={symbol} />
            <PositionSummary />
            <ProfessionalOrderBook symbol={symbol} />
            <RecentTradeTape symbol={symbol} />
            <DepthChartPanel symbol={symbol} />
          </div>
        </WorkspacePanel>
      </div>
    </div>
  );
}

function WorkspacePanel({
  collapsed,
  label,
  onToggle,
  children
}: Readonly<{ collapsed: boolean; label: string; onToggle: () => void; children: React.ReactNode }>) {
  if (collapsed) {
    return (
      <button
        className="glass-panel hidden min-h-96 rounded-lg p-2 text-micro font-semibold uppercase text-muted-foreground transition hover:text-foreground xl:block"
        onClick={onToggle}
        type="button"
      >
        <span className="inline-block rotate-180" style={{ writingMode: "vertical-rl" }}>{label}</span>
      </button>
    );
  }
  return (
    <aside className="min-w-0 space-y-3">
      <div className="flex justify-end">
        <Button onClick={onToggle} size="sm" type="button" variant="ghost">Collapse</Button>
      </div>
      {children}
    </aside>
  );
}

function MarketWorkspaceSidebar({ selected }: Readonly<{ selected: string }>) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<MarketCategory>("all");
  const marketsQuery = useQuery({ queryKey: queryKeys.dashboardMarkets, queryFn: heliumApi.dashboardMarkets });
  const watchlistQuery = useQuery({ queryKey: queryKeys.watchlist, queryFn: heliumApi.watchlist });
  const watchlist = useMemo(() => watchlistQuery.data ?? [], [watchlistQuery.data]);
  const watchlistSymbols = useMemo(() => new Set(watchlist.map((item) => item.marketSymbol)), [watchlist]);
  const pinnedSymbols = useMemo(() => new Set(watchlist.filter((item) => item.pinned).map((item) => item.marketSymbol)), [watchlist]);
  const markets = useMemo(() => {
    const rows = marketsQuery.data ?? [];
    return rows
      .filter((market) => market.marketSymbol.toLowerCase().includes(search.toLowerCase()))
      .filter((market) => category === "all" || category === "spot" || (category === "favorites" && watchlistSymbols.has(market.marketSymbol)) || (category === "pinned" && pinnedSymbols.has(market.marketSymbol)));
  }, [category, marketsQuery.data, pinnedSymbols, search, watchlistSymbols]);

  const toggleFavorite = async (market: DashboardMarketCard) => {
    if (watchlistSymbols.has(market.marketSymbol)) {
      await heliumApi.removeWatchlistItem(market.marketSymbol);
    } else {
      await heliumApi.upsertWatchlistItem({ marketSymbol: market.marketSymbol, pinned: false, sortOrder: watchlist.length });
    }
    void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
  };

  return (
    <Card className="sticky top-20 max-h-[calc(100vh-6rem)] overflow-hidden">
      <CardHeader>
        <CardTitle>Markets</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Search onChange={(event) => setSearch(event.target.value)} placeholder="Search markets" value={search} />
        <div className="grid grid-cols-4 gap-1">
          {(["all", "favorites", "pinned", "spot"] as const).map((item) => (
            <button
              className={cn("h-8 rounded-sm border border-border/70 text-micro font-semibold uppercase text-muted-foreground transition hover:bg-white/8", category === item && "bg-white/10 text-foreground")}
              key={item}
              onClick={() => setCategory(item)}
              type="button"
            >
              {item === "favorites" ? "Fav" : item}
            </button>
          ))}
        </div>
        {marketsQuery.isLoading ? <LoadingState label="Loading markets" /> : null}
        {marketsQuery.isError ? <ErrorState title="Could not load markets" error={marketsQuery.error} onRetry={() => void marketsQuery.refetch()} /> : null}
        <div className="max-h-[64vh] space-y-1 overflow-auto pr-1">
          {markets.map((market) => {
            const watchlistItem = watchlist.find((item) => item.marketSymbol === market.marketSymbol);
            return (
            <div className={cn("grid grid-cols-[1fr_auto_auto] items-center gap-2 rounded-md border border-border/70 p-2 transition hover:bg-white/[0.045]", selected === market.marketSymbol && "border-cyan-300/40 bg-cyan-300/10")} key={market.marketSymbol}>
              <Link href={`/trade?symbol=${market.marketSymbol}`}>
                <div className="flex items-center justify-between gap-3">
                  <span className="font-semibold text-foreground">{market.marketSymbol}</span>
                  <PriceMove value={market.priceChangePercent24h} />
                </div>
                <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
                  <span>{market.baseAsset}/{market.quoteAsset}</span>
                  <span className="font-mono">{formatAmount(market.currentPrice, 2)}</span>
                </div>
              </Link>
              <button
                aria-label={watchlistSymbols.has(market.marketSymbol) ? "Remove favorite" : "Add favorite"}
                className={cn("h-8 rounded-sm border border-border/70 px-2 text-[0px] font-semibold uppercase text-muted-foreground transition hover:bg-white/8", watchlistItem && "border-amber-300/30 bg-amber-300/12 text-amber-200")}
                onClick={() => void toggleFavorite(market)}
                type="button"
              >
                <span className="text-[10px]">{watchlistItem ? "Fav" : "Add"}</span>
                {watchlistSymbols.has(market.marketSymbol) ? "★" : "☆"}
              </button>
              <button
                className={cn("h-8 rounded-sm border border-border/70 px-2 text-micro font-semibold uppercase text-muted-foreground transition hover:bg-white/8 disabled:opacity-40", watchlistItem?.pinned && "border-cyan-300/30 bg-cyan-300/12 text-cyan-100")}
                disabled={!watchlistItem}
                onClick={() => {
                  if (!watchlistItem) return;
                  void heliumApi.upsertWatchlistItem({ marketSymbol: market.marketSymbol, pinned: !watchlistItem.pinned, sortOrder: watchlistItem.sortOrder })
                    .then(() => {
                      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist });
                      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
                    });
                }}
                type="button"
              >
                Pin
              </button>
            </div>
          );})}
        </div>
      </CardContent>
    </Card>
  );
}

function WorkspaceTicker({ symbol }: Readonly<{ symbol: string }>) {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.ticker(symbol), queryFn: () => heliumApi.ticker(symbol) });
  const status = useMarketStream(symbol, "ticker", (event) => {
    if (event.type === "ticker") {
      queryClient.setQueryData(queryKeys.ticker(symbol), event.payload);
    }
  });

  if (query.isLoading) return <LoadingState label="Loading ticker" />;
  if (query.isError) return <ErrorState title="Could not load ticker" error={query.error} onRetry={() => void query.refetch()} />;
  const ticker = query.data;
  if (!ticker) return <EmptyState title="No ticker data" />;
  return (
    <div className="glass-panel sticky top-0 z-20 flex flex-wrap items-center gap-4 rounded-lg px-4 py-3 text-sm">
      <Link className="text-lg font-semibold text-foreground hover:text-cyan-100" href={`/trade?symbol=${ticker.market}`}>{ticker.market}</Link>
      <span className="font-mono text-xl text-slate-100">{formatAmount(ticker.lastPrice, 2)}</span>
      <PriceMove value={ticker.openPrice24h ? ((ticker.lastPrice - ticker.openPrice24h) / ticker.openPrice24h) * 100 : null} />
      <TickerMetric label="High" value={ticker.highPrice24h} />
      <TickerMetric label="Low" value={ticker.lowPrice24h} />
      <TickerMetric label="Volume" value={ticker.volume24h} />
      <TickerMetric label="Bid" value={ticker.bestBid} tone="bid" />
      <TickerMetric label="Ask" value={ticker.bestAsk} tone="ask" />
      <span className="ml-auto"><Badge tone={status === "connected" ? "success" : "warning"}>{status === "connected" ? "Live" : "Syncing"}</Badge></span>
    </div>
  );
}

function AdvancedChartPanel({ symbol }: Readonly<{ symbol: string }>) {
  const queryClient = useQueryClient();
  const [timeframe, setTimeframe] = useState("1m");
  const [fullscreen, setFullscreen] = useState(false);
  const [activeIndicators, setActiveIndicators] = useState<string[]>([]);
  const query = useQuery({ queryKey: queryKeys.candles(symbol, timeframe), queryFn: () => heliumApi.candles(symbol, timeframe) });

  const toggleIndicator = (indicator: string) => {
    setActiveIndicators((current) => current.includes(indicator) ? current.filter((item) => item !== indicator) : [...current, indicator]);
  };

  return (
    <Card className={cn("terminal-grid overflow-hidden", fullscreen && "fixed inset-3 z-50")}>
      <CardHeader className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle>Advanced Chart</CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            {TIMEFRAMES.map((item) => (
              <button
                className={cn("h-8 rounded-sm border border-border/70 px-2 text-xs font-semibold text-muted-foreground transition hover:bg-white/8", timeframe === item && "border-cyan-300/30 bg-cyan-300/12 text-cyan-100", item !== "1m" && "opacity-45")}
                disabled={item !== "1m"}
                key={item}
                onClick={() => setTimeframe(item)}
                type="button"
              >
                {item}
              </button>
            ))}
            <Button onClick={() => setFullscreen(!fullscreen)} size="sm" type="button" variant="secondary">{fullscreen ? "Exit" : "Fullscreen"}</Button>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {INDICATORS.map((indicator) => (
            <button
              className={cn("h-7 rounded-sm border border-border/70 px-2 text-micro font-semibold uppercase text-muted-foreground transition hover:bg-white/8", activeIndicators.includes(indicator) && "bg-white/10 text-foreground")}
              key={indicator}
              onClick={() => toggleIndicator(indicator)}
              type="button"
            >
              {indicator}
            </button>
          ))}
          {DRAWING_TOOLS.map((tool) => (
            <button className="h-7 rounded-sm border border-border/70 px-2 text-micro font-semibold uppercase text-muted-foreground opacity-55" disabled key={tool} type="button">
              {tool}
            </button>
          ))}
        </div>
      </CardHeader>
      <CardContent>
        <ChartRealtimeBridge enabled={timeframe === "1m"} onCandles={(candles) => queryClient.setQueryData(queryKeys.candles(symbol, "1m"), candles)} symbol={symbol} />
        {query.isLoading ? <LoadingState label="Loading candles" /> : null}
        {query.isError ? <ErrorState title="Could not load candles" error={query.error} onRetry={() => void query.refetch()} /> : null}
        {query.data?.length ? <CandlestickChart candles={query.data} className={fullscreen ? "h-[calc(100vh-12rem)]" : "h-[520px]"} /> : null}
        {!query.isLoading && !query.isError && !query.data?.length ? <EmptyState title="No candle data" /> : null}
      </CardContent>
    </Card>
  );
}

function ChartRealtimeBridge({ enabled, symbol, onCandles }: Readonly<{ enabled: boolean; symbol: string; onCandles: (candles: CandleResponse[]) => void }>) {
  if (!enabled) return null;
  return <CandleStream symbol={symbol} onCandles={onCandles} />;
}

function CandleStream({ symbol, onCandles }: Readonly<{ symbol: string; onCandles: (candles: CandleResponse[]) => void }>) {
  useMarketStream(symbol, "candles", (event) => {
    if (event.type === "candles" && Array.isArray(event.payload)) {
      onCandles(event.payload as CandleResponse[]);
    }
  });
  return null;
}

function ProfessionalOrderBook({ symbol }: Readonly<{ symbol: string }>) {
  const queryClient = useQueryClient();
  const [precision, setPrecision] = useState(2);
  const query = useQuery({ queryKey: queryKeys.orderBook(symbol), queryFn: () => heliumApi.orderBook(symbol) });
  const status = useMarketStream(symbol, "orderbook", (event) => {
    if (event.type === "orderbook") queryClient.setQueryData(queryKeys.orderBook(symbol), event.payload);
  });

  if (query.isLoading) return <LoadingState label="Loading order book" />;
  if (query.isError) return <ErrorState title="Could not load order book" error={query.error} onRetry={() => void query.refetch()} />;

  const book = query.data;
  const bids = aggregateLevels(book?.bids ?? [], precision, "bid").slice(0, 12);
  const asks = aggregateLevels(book?.asks ?? [], precision, "ask").slice(0, 12);
  const bestBid = bids[0]?.price ?? null;
  const bestAsk = asks[0]?.price ?? null;
  const spread = bestBid !== null && bestAsk !== null ? bestAsk - bestBid : null;

  return (
    <Card className="resize-y overflow-auto">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Order Book</CardTitle>
        <div className="flex items-center gap-2">
          <select className="h-8 rounded-sm border border-border bg-black/20 px-2 text-xs" onChange={(event) => setPrecision(Number(event.target.value))} value={precision}>
            {[0, 1, 2, 3, 4].map((item) => <option key={item} value={item}>{item} dp</option>)}
          </select>
          <Badge tone={status === "connected" ? "success" : "warning"}>{status === "connected" ? "Live" : "Sync"}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-2">
        <BestQuote bid={bestBid} ask={bestAsk} spread={spread} />
        <BookSide levels={[...asks].reverse()} side="ask" />
        <div className="rounded-sm border border-border/70 bg-white/[0.035] px-2 py-1 text-center text-xs text-muted-foreground">
          Spread <span className="font-mono text-amber-200">{formatAmount(spread, precision + 2)}</span>
        </div>
        <BookSide levels={bids} side="bid" />
      </CardContent>
    </Card>
  );
}

function RecentTradeTape({ symbol }: Readonly<{ symbol: string }>) {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.publicTrades(symbol), queryFn: () => heliumApi.publicTrades(symbol) });
  const status = useMarketStream(symbol, "trades", (event) => {
    if (event.type === "trades") queryClient.setQueryData(queryKeys.publicTrades(symbol), event.payload);
  });

  if (query.isLoading) return <LoadingState label="Loading trades" />;
  if (query.isError) return <ErrorState title="Could not load trades" error={query.error} onRetry={() => void query.refetch()} />;
  const trades = (query.data ?? []).slice(0, 80);
  return (
    <Card className="resize-y overflow-auto">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Trade Tape</CardTitle>
        <Badge tone={status === "connected" ? "success" : "warning"}>{status === "connected" ? "Live" : "Sync"}</Badge>
      </CardHeader>
      <CardContent>
        {!trades.length ? <EmptyState title="No recent trades" /> : null}
        <div className="max-h-72 overflow-auto pr-1">
          {trades.map((trade) => (
            <div className="grid grid-cols-[1fr_1fr_72px_52px] gap-2 rounded-sm px-2 py-1.5 text-xs transition hover:bg-white/[0.04]" key={trade.executionId}>
              <span className={cn("font-mono", trade.buyerMaker ? "text-red-300" : "text-emerald-300")}>{formatAmount(trade.price, 2)}</span>
              <span className="font-mono text-slate-200">{formatAmount(trade.quantity)}</span>
              <span className="text-muted-foreground">{shortTime(trade.tradedAt)}</span>
              <span className="text-right text-muted-foreground">{trade.buyerMaker ? "Maker" : "Taker"}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function DepthChartPanel({ symbol }: Readonly<{ symbol: string }>) {
  const query = useQuery({ queryKey: queryKeys.orderBook(symbol), queryFn: () => heliumApi.orderBook(symbol) });
  if (query.isLoading) return <LoadingState label="Loading depth" />;
  if (query.isError) return <ErrorState title="Could not load depth" error={query.error} onRetry={() => void query.refetch()} />;
  const depth = cumulativeDepth(query.data);
  return (
    <Card>
      <CardHeader><CardTitle>Depth</CardTitle></CardHeader>
      <CardContent>
        {!depth.length ? <EmptyState title="No depth available" /> : <DepthChart levels={depth} />}
      </CardContent>
    </Card>
  );
}

function LowerWorkspacePanel() {
  const [active, setActive] = useState<LowerTab>("Open Orders");
  const [search, setSearch] = useState("");
  return (
    <Card className="resize-y overflow-auto">
      <CardHeader className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-1 rounded-md border border-border bg-black/20 p-1">
            {LOWER_TABS.map((tab) => (
              <button
                className={cn("h-8 rounded-sm px-3 text-xs font-semibold text-muted-foreground transition hover:bg-white/8", active === tab && "bg-white/10 text-foreground")}
                key={tab}
                onClick={() => setActive(tab)}
                type="button"
              >
                {tab}
              </button>
            ))}
          </div>
          <Search className="w-full max-w-xs" onChange={(event) => setSearch(event.target.value)} placeholder={`Search ${active.toLowerCase()}`} value={search} />
        </div>
      </CardHeader>
      <CardContent>
        {active === "Open Orders" ? <SearchableOpenOrders search={search} /> : null}
        {active === "Order History" ? <SearchableOrderHistory search={search} /> : null}
        {active === "Trade History" ? <SearchableTradeHistory search={search} /> : null}
        {active === "Portfolio" ? <PortfolioTab search={search} /> : null}
        {active === "Activity" ? <ActivityTab search={search} /> : null}
      </CardContent>
    </Card>
  );
}

function SearchableOpenOrders({ search }: Readonly<{ search: string }>) {
  const queryClient = useQueryClient();
  const [toast, setToast] = useState<{ message: string; tone: "success" | "danger" } | null>(null);
  const query = useQuery({ queryKey: queryKeys.openOrders, queryFn: heliumApi.openOrders });
  const cancel = useMutation({
    mutationFn: heliumApi.cancelOrder,
    onSuccess: () => {
      setToast({ message: "Cancellation requested.", tone: "success" });
      void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardPortfolio });
    },
    onError: (error) => setToast({ message: errorMessage(error), tone: "danger" })
  });

  if (query.isLoading) return <LoadingState label="Loading open orders" />;
  if (query.isError) return <ErrorState title="Could not load open orders" error={query.error} onRetry={() => void query.refetch()} />;
  const rows = (query.data ?? [])
    .filter((order) => JSON.stringify(order).toLowerCase().includes(search.toLowerCase()))
    .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
    .slice(0, 100);
  if (!rows.length) return <EmptyState title="No matching open orders" />;
  return (
    <>
      <DataTable
        columns={["Market", "Side", "Price", "Qty", "Filled", "Remaining", "Status", "Updated", "Action"]}
        rows={rows.map((order) => [
          order.marketSymbol,
          order.side,
          formatAmount(order.limitPrice),
          formatAmount(order.quantity),
          formatAmount(order.filledQuantity),
          formatAmount(order.remainingQuantity),
          order.status,
          shortDate(order.updatedAt),
          <Button disabled={cancel.isPending} key={order.id} onClick={() => cancel.mutate(order.id)} size="sm" type="button" variant="secondary">Cancel</Button>
        ])}
      />
      <Toast message={toast?.message} tone={toast?.tone} />
    </>
  );
}

function SearchableOrderHistory({ search }: Readonly<{ search: string }>) {
  const query = useQuery({ queryKey: queryKeys.orderHistory, queryFn: heliumApi.orderHistory });
  if (query.isLoading) return <LoadingState label="Loading orders" />;
  if (query.isError) return <ErrorState title="Could not load orders" error={query.error} onRetry={() => void query.refetch()} />;
  const rows = (query.data ?? []).filter((order) => JSON.stringify(order).toLowerCase().includes(search.toLowerCase())).slice(0, 100);
  if (!rows.length) return <EmptyState title="No matching orders" />;
  return (
    <DataTable
      columns={["Market", "Side", "Type", "Price", "Qty", "Filled", "Status", "Updated"]}
      rows={rows.map((order) => [order.marketSymbol, order.side, order.orderType, formatAmount(order.limitPrice), formatAmount(order.quantity), formatAmount(order.filledQuantity), order.status, shortDate(order.updatedAt)])}
    />
  );
}

function SearchableTradeHistory({ search }: Readonly<{ search: string }>) {
  const query = useQuery({ queryKey: queryKeys.trades, queryFn: heliumApi.tradeHistory });
  if (query.isLoading) return <LoadingState label="Loading trades" />;
  if (query.isError) return <ErrorState title="Could not load trades" error={query.error} onRetry={() => void query.refetch()} />;
  const rows = (query.data ?? []).filter((trade) => JSON.stringify(trade).toLowerCase().includes(search.toLowerCase())).slice(0, 100);
  if (!rows.length) return <EmptyState title="No matching trades" />;
  return (
    <div className="space-y-3">
      <CsvButton filename="helium-trades.csv" rows={rows.map((trade) => ({ market: trade.market, side: trade.side, price: trade.price, quantity: trade.quantity, fee: trade.fee, time: trade.time }))} />
      <DataTable
        columns={["Market", "Side", "Price", "Qty", "Fee", "Time"]}
        rows={rows.map((trade) => [trade.market, trade.side, formatAmount(trade.price), formatAmount(trade.quantity), formatAmount(trade.fee), shortDate(trade.time)])}
      />
    </div>
  );
}

function PortfolioTab({ search }: Readonly<{ search: string }>) {
  const query = useQuery({ queryKey: queryKeys.dashboardPortfolio, queryFn: heliumApi.dashboardPortfolio });
  if (query.isLoading) return <LoadingState label="Loading portfolio" />;
  if (query.isError) return <ErrorState title="Could not load portfolio" error={query.error} onRetry={() => void query.refetch()} />;
  const assets = (query.data?.assets ?? []).filter((asset) => asset.asset.toLowerCase().includes(search.toLowerCase()));
  if (!assets.length) return <EmptyState title="No matching assets" />;
  return (
    <DataTable
      columns={["Asset", "Available", "Locked", "Value", "Allocation", "PnL"]}
      rows={assets.map((asset) => [asset.asset, formatAmount(asset.available), formatAmount(asset.locked), formatAmount(asset.marketValue, 2), `${formatAmount(asset.allocationPercent, 2)}%`, <Pnl key={asset.asset} value={asset.unrealizedPnl} />])}
    />
  );
}

function ActivityTab({ search }: Readonly<{ search: string }>) {
  const query = useQuery({ queryKey: queryKeys.dashboardActivity, queryFn: heliumApi.dashboardActivity });
  if (query.isLoading) return <LoadingState label="Loading activity" />;
  if (query.isError) return <ErrorState title="Could not load activity" error={query.error} onRetry={() => void query.refetch()} />;
  const rows = (query.data ?? []).filter((item) => JSON.stringify(item).toLowerCase().includes(search.toLowerCase())).slice(0, 100);
  if (!rows.length) return <EmptyState title="No matching activity" />;
  return (
    <DataTable
      columns={["Category", "Event", "Summary", "Time"]}
      rows={rows.map((item: ActivityItem) => [item.category, humanize(item.eventType), item.summary, shortDate(item.occurredAt)])}
    />
  );
}

function TradingStreamBridge({ onEvent }: Readonly<{ onEvent: (event: { type: string }) => void }>) {
  useTradingStream(onEvent);
  return null;
}

type BookLevel = {
  price: number;
  quantity: number;
  total: number;
};

function aggregateLevels(orders: BookOrder[], precision: number, side: "bid" | "ask"): BookLevel[] {
  const scale = 10 ** precision;
  const grouped = new Map<number, number>();
  orders.forEach((order) => {
    const rounded = side === "bid" ? Math.floor(order.price * scale) / scale : Math.ceil(order.price * scale) / scale;
    grouped.set(rounded, (grouped.get(rounded) ?? 0) + order.remainingQuantity);
  });
  return [...grouped.entries()]
    .map(([price, quantity]) => ({ price, quantity, total: price * quantity }))
    .sort((left, right) => side === "bid" ? right.price - left.price : left.price - right.price);
}

function BookSide({ levels, side }: Readonly<{ levels: BookLevel[]; side: "bid" | "ask" }>) {
  const max = Math.max(...levels.map((level) => level.total), Number.EPSILON);
  return (
    <div className="space-y-1">
      {levels.map((level) => (
        <div className="relative grid grid-cols-3 gap-2 overflow-hidden rounded-sm px-2 py-1.5 text-xs" key={`${side}-${level.price}`}>
          <span
            aria-hidden
            className={cn("absolute inset-y-0 right-0 transition-all", side === "bid" ? "bg-emerald-400/12" : "bg-red-400/12")}
            style={{ width: `${Math.min(100, (level.total / max) * 100)}%` }}
          />
          <span className={cn("relative font-mono", side === "bid" ? "text-emerald-300" : "text-red-300")}>{formatAmount(level.price, 4)}</span>
          <span className="relative font-mono text-slate-200">{formatAmount(level.quantity)}</span>
          <span className="relative text-right font-mono text-muted-foreground">{formatAmount(level.total, 2)}</span>
        </div>
      ))}
    </div>
  );
}

function BestQuote({ bid, ask, spread }: Readonly<{ bid: number | null; ask: number | null; spread: number | null }>) {
  return (
    <div className="grid grid-cols-3 gap-2 rounded-md border border-border/70 bg-black/18 p-2 text-xs">
      <Metric label="Best Bid" value={formatAmount(bid, 2)} tone="bid" />
      <Metric label="Spread" value={formatAmount(spread, 4)} />
      <Metric label="Best Ask" value={formatAmount(ask, 2)} tone="ask" />
    </div>
  );
}

function cumulativeDepth(book: OrderBookView | undefined) {
  const bids = aggregateLevels(book?.bids ?? [], 2, "bid").slice(0, 25);
  const asks = aggregateLevels(book?.asks ?? [], 2, "ask").slice(0, 25);
  let bidTotal = 0;
  let askTotal = 0;
  const bidDepth = bids.map((level) => ({ ...level, side: "bid" as const, cumulative: (bidTotal += level.quantity) })).reverse();
  const askDepth = asks.map((level) => ({ ...level, side: "ask" as const, cumulative: (askTotal += level.quantity) }));
  return [...bidDepth, ...askDepth];
}

function DepthChart({ levels }: Readonly<{ levels: ReturnType<typeof cumulativeDepth> }>) {
  const max = Math.max(...levels.map((level) => level.cumulative), Number.EPSILON);
  return (
    <div className="flex h-44 items-end gap-px rounded-md border border-border/70 bg-black/18 p-2">
      {levels.map((level) => (
        <div className="group relative flex-1" key={`${level.side}-${level.price}`}>
          <div
            className={cn("w-full rounded-t-sm transition", level.side === "bid" ? "bg-emerald-400/45 group-hover:bg-emerald-300/80" : "bg-red-400/45 group-hover:bg-red-300/80")}
            style={{ height: `${Math.max(2, (level.cumulative / max) * 100)}%` }}
          />
          <div className="pointer-events-none absolute bottom-full left-1/2 z-10 hidden -translate-x-1/2 rounded-sm border border-border bg-slate-950 px-2 py-1 text-xs group-hover:block">
            <p className="font-mono">{formatAmount(level.price, 2)}</p>
            <p className="font-mono text-muted-foreground">{formatAmount(level.cumulative)}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

function CsvButton({ filename, rows }: Readonly<{ filename: string; rows: Record<string, unknown>[] }>) {
  const exportCsv = () => {
    const headers = Object.keys(rows[0] ?? {});
    const body = rows.map((row) => headers.map((header) => JSON.stringify(row[header] ?? "")).join(","));
    const csv = [headers.join(","), ...body].join("\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  };
  return <Button disabled={!rows.length} onClick={exportCsv} size="sm" type="button" variant="secondary">Export CSV</Button>;
}

function PriceMove({ value }: Readonly<{ value: number | null }>) {
  if (value === null) return <Badge tone="info">—</Badge>;
  return <Badge tone={value < 0 ? "danger" : "success"}>{`${value < 0 ? "" : "+"}${formatAmount(value, 2)}%`}</Badge>;
}

function TickerMetric({ label, value, tone }: Readonly<{ label: string; value: number | null; tone?: "bid" | "ask" }>) {
  return (
    <span className="text-muted-foreground">{label} <span className={cn("font-mono text-slate-300", tone === "bid" && "text-emerald-300", tone === "ask" && "text-red-300")}>{formatAmount(value, 2)}</span></span>
  );
}

function Metric({ label, value, tone }: Readonly<{ label: string; value: string; tone?: "bid" | "ask" }>) {
  return (
    <div>
      <p className="text-micro font-semibold uppercase text-muted-foreground">{label}</p>
      <p className={cn("mt-1 font-mono text-sm text-slate-200", tone === "bid" && "text-emerald-300", tone === "ask" && "text-red-300")}>{value}</p>
    </div>
  );
}

function Pnl({ value }: Readonly<{ value: number | null }>) {
  if (value === null) return <span>—</span>;
  return <span className={cn("font-mono", value < 0 ? "text-red-300" : "text-emerald-300")}>{value < 0 ? "" : "+"}{formatAmount(value, 2)}</span>;
}

function humanize(value: string) {
  return value.replaceAll("_", " ").replaceAll(".", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function usePersistedFlag(key: string, initialValue: boolean) {
  const [value, setValue] = useState(initialValue);
  useEffect(() => {
    try {
      setValue(localStorage.getItem(key) === "true");
    } catch {
      setValue(initialValue);
    }
  }, [initialValue, key]);
  const update = (next: boolean) => {
    setValue(next);
    try {
      localStorage.setItem(key, String(next));
    } catch {
      // Layout persistence is optional; backend data remains the source of truth.
    }
  };
  return [value, update] as const;
}
