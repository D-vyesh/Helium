"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/state";
import { useAuthStore } from "@/features/auth/store";
import { heliumApi } from "@/lib/api/client";
import type { DashboardMarketCard, MarketView, OrderView, PortfolioAsset, PriceAlert, TradeRecord, WatchlistItem } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { cn } from "@/lib/utils/cn";
import { formatAmount, shortDate } from "@/lib/utils/format";
import { useQuery } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";

type CommandGroup = "Market" | "Page" | "Setting" | "Order" | "Portfolio" | "Help" | "Admin" | "Alert" | "Recent";

type CommandItem = {
  id: string;
  group: CommandGroup;
  title: string;
  subtitle: string;
  href?: string;
  action?: () => void;
  keywords: string[];
  tone?: "neutral" | "success" | "danger" | "warning" | "info";
};

const ADMIN_ROLES = ["ADMIN", "TREASURY_ADMIN", "SECURITY_ADMIN", "COMPLIANCE_OFFICER", "AUDITOR", "RISK_MANAGER"];

const PAGE_COMMANDS: CommandItem[] = [
  command("page-dashboard", "Page", "Dashboard", "Portfolio, watchlist, activity, and exchange health", "/dashboard", ["home", "portfolio", "watchlist"]),
  command("page-markets", "Page", "Markets", "Live spot instruments and market data", "/markets", ["spot", "symbols", "prices"]),
  command("page-trade", "Page", "Trading Terminal", "Chart, order book, order entry, and execution history", "/trade", ["buy", "sell", "orders"]),
  command("page-wallet", "Page", "Wallet", "Balances, deposits, and withdrawals", "/wallet", ["balances", "assets"]),
  command("page-open-orders", "Page", "Open Orders", "Working orders awaiting fills or cancellation", "/orders/open", ["active orders"]),
  command("page-order-history", "Page", "Order History", "Submitted, filled, cancelled, and rejected orders", "/orders/history", ["orders"]),
  command("page-trade-history", "Page", "Trade History", "Executed trades and fees", "/trades/history", ["fills", "executions"]),
  command("page-settings", "Page", "Settings", "Security, sessions, MFA, password, and price alerts", "/settings", ["security", "mfa", "password", "alerts"]),
  command("page-wallet-deposit", "Page", "Deposit Addresses", "View existing deposit addresses", "/wallet/deposit", ["deposit", "addresses"]),
  command("page-wallet-withdraw", "Page", "Withdraw", "Submit a withdrawal request", "/wallet/withdraw", ["withdrawal"])
];

const SETTING_COMMANDS: CommandItem[] = [
  command("setting-security", "Setting", "Security Settings", "Password, MFA, backup codes, and active sessions", "/settings#security", ["mfa", "totp", "sessions", "password"], "warning"),
  command("setting-price-alerts", "Setting", "Price Alerts", "Create and manage server-side alert rules", "/settings#price-alerts", ["alerts", "notifications", "markets"], "info"),
  command("setting-sessions", "Setting", "Active Sessions", "Review current devices and revoke sessions", "/settings#sessions", ["devices", "logout", "session"], "warning")
];

const HELP_ITEMS = [
  ["Ctrl/Cmd + K", "Open command palette"],
  ["/", "Focus global search"],
  ["?", "Open shortcut help"],
  ["B", "Set order ticket to buy"],
  ["S", "Set order ticket to sell"],
  ["Ctrl/Cmd + Enter", "Submit the active order ticket"],
  ["Esc", "Close dialogs"]
] as const;

export function CommandPalette() {
  const router = useRouter();
  const pathname = usePathname();
  const user = useAuthStore((state) => state.user);
  const canUseAdmin = Boolean(user?.roles.some((role) => ADMIN_ROLES.includes(role)));
  const [open, setOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const [recents, setRecents] = useState<StoredRecent[]>([]);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const owner = user?.id ?? "local";
  const recentKey = `helium.command.recent.${owner}`;

  const marketsQuery = useQuery({ queryKey: queryKeys.markets, queryFn: heliumApi.markets, enabled: open });
  const dashboardMarketsQuery = useQuery({ queryKey: queryKeys.dashboardMarkets, queryFn: heliumApi.dashboardMarkets, enabled: open });
  const watchlistQuery = useQuery({ queryKey: queryKeys.watchlist, queryFn: heliumApi.watchlist, enabled: open });
  const portfolioQuery = useQuery({ queryKey: queryKeys.dashboardPortfolio, queryFn: heliumApi.dashboardPortfolio, enabled: open });
  const openOrdersQuery = useQuery({ queryKey: queryKeys.openOrders, queryFn: heliumApi.openOrders, enabled: open });
  const orderHistoryQuery = useQuery({ queryKey: queryKeys.orderHistory, queryFn: heliumApi.orderHistory, enabled: open });
  const tradesQuery = useQuery({ queryKey: queryKeys.trades, queryFn: heliumApi.tradeHistory, enabled: open });
  const priceAlertsQuery = useQuery({ queryKey: queryKeys.priceAlerts, queryFn: heliumApi.priceAlerts, enabled: open });

  useEffect(() => {
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setOpen(true);
        return;
      }
      if (event.key === "/" && !isTypingTarget(event.target)) {
        event.preventDefault();
        setOpen(true);
        return;
      }
      if (event.key === "?" && !isTypingTarget(event.target)) {
        event.preventDefault();
        setHelpOpen(true);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => inputRef.current?.focus(), 20);
    return () => window.clearTimeout(timer);
  }, [open]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      const stored = window.localStorage.getItem(recentKey);
      setRecents(stored ? (JSON.parse(stored) as StoredRecent[]) : []);
    } catch {
      setRecents([]);
    }
  }, [recentKey]);

  const allCommands = useMemo(() => {
    const items: CommandItem[] = [
      ...PAGE_COMMANDS,
      ...SETTING_COMMANDS,
      ...marketCommands(marketsQuery.data ?? [], dashboardMarketsQuery.data ?? [], watchlistQuery.data ?? []),
      ...portfolioCommands(portfolioQuery.data?.assets ?? []),
      ...orderCommands([...(openOrdersQuery.data ?? []), ...(orderHistoryQuery.data ?? [])]),
      ...tradeCommands(tradesQuery.data ?? []),
      ...alertCommands(priceAlertsQuery.data ?? []),
      {
        id: "help-shortcuts",
        group: "Help",
        title: "Keyboard Shortcuts",
        subtitle: "Open the shortcut reference",
        action: () => setHelpOpen(true),
        keywords: ["help", "shortcuts", "keyboard", "commands"],
        tone: "info"
      }
    ];
    if (canUseAdmin) {
      items.push(
        command("admin-overview", "Admin", "Admin", "Operational workspace", "/admin", ["users", "markets", "withdrawals"], "warning"),
        command("admin-reconciliation", "Admin", "Reconciliation", "Ledger and accounting controls", "/admin/reconciliation", ["audit", "ledger", "finance"], "warning")
      );
    }
    return items;
  }, [
    canUseAdmin,
    dashboardMarketsQuery.data,
    marketsQuery.data,
    openOrdersQuery.data,
    orderHistoryQuery.data,
    portfolioQuery.data?.assets,
    priceAlertsQuery.data,
    tradesQuery.data,
    watchlistQuery.data
  ]);

  const results = useMemo(() => {
    const recentItems = recents
      .map((recent) => allCommands.find((item) => item.id === recent.id))
      .filter((item): item is CommandItem => Boolean(item))
      .map((item) => ({ ...item, group: "Recent" as const }));
    const ranked = rankCommands(allCommands, query);
    if (query.trim()) return ranked.slice(0, 24);
    return [...recentItems.slice(0, 5), ...ranked.filter((item) => !recentItems.some((recent) => recent.id === item.id)).slice(0, 19)];
  }, [allCommands, query, recents]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query, open]);

  const runCommand = (item: CommandItem) => {
    rememberCommand(recentKey, item);
    setRecents((current) => [{ id: item.id, title: item.title }, ...current.filter((recent) => recent.id !== item.id)].slice(0, 8));
    setOpen(false);
    setQuery("");
    if (item.action) {
      item.action();
      return;
    }
    if (item.href) {
      router.push(item.href);
    }
  };

  const handleInputKeys = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((value) => Math.min(results.length - 1, value + 1));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((value) => Math.max(0, value - 1));
      return;
    }
    if (event.key === "Enter" && results[activeIndex]) {
      event.preventDefault();
      runCommand(results[activeIndex]);
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      setOpen(false);
    }
  };

  return (
    <>
      <Button
        aria-label="Open command palette"
        className="hidden min-w-44 justify-between border-border/80 bg-white/[0.045] font-normal text-muted-foreground sm:inline-flex"
        onClick={() => setOpen(true)}
        size="md"
        type="button"
        variant="secondary"
      >
        <span className="truncate">Search</span>
        <kbd className="rounded-sm border border-border bg-black/24 px-1.5 py-0.5 font-mono text-[10px] text-slate-300">Ctrl K</kbd>
      </Button>
      <Button className="sm:hidden" onClick={() => setOpen(true)} size="sm" type="button" variant="secondary">
        Search
      </Button>

      <Dialog open={open} title="Command Palette" onClose={() => setOpen(false)}>
        <div className="space-y-3">
          <input
            aria-activedescendant={results[activeIndex]?.id}
            aria-autocomplete="list"
            aria-controls="helium-command-results"
            aria-expanded={open}
            className="h-12 w-full rounded-md border border-border bg-black/28 px-3 text-sm text-foreground outline-none transition placeholder:text-muted-foreground focus:border-primary focus:ring-2 focus:ring-primary/20"
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={handleInputKeys}
            placeholder="Search markets, orders, portfolio, settings"
            ref={inputRef}
            role="combobox"
            value={query}
          />
          <div className="flex flex-wrap items-center gap-2 text-micro uppercase text-muted-foreground">
            <Badge tone={connectionTone([marketsQuery, dashboardMarketsQuery, portfolioQuery, openOrdersQuery, orderHistoryQuery, tradesQuery, priceAlertsQuery])}>
              {connectionLabel([marketsQuery, dashboardMarketsQuery, portfolioQuery, openOrdersQuery, orderHistoryQuery, tradesQuery, priceAlertsQuery])}
            </Badge>
            <span>{pathname}</span>
          </div>
          <div className="max-h-[60vh] overflow-auto pr-1" id="helium-command-results" role="listbox">
            {!results.length ? <EmptyState title="No matching command" detail="Try a market symbol, order status, asset, setting, or page." /> : null}
            {results.map((item, index) => (
              <button
                aria-selected={activeIndex === index}
                className={cn(
                  "grid w-full grid-cols-[auto_1fr_auto] items-center gap-3 rounded-md px-3 py-2 text-left transition",
                  activeIndex === index ? "bg-cyan-300/12 text-foreground" : "text-muted-foreground hover:bg-white/8 hover:text-foreground"
                )}
                id={item.id}
                key={`${item.group}-${item.id}`}
                onClick={() => runCommand(item)}
                onMouseEnter={() => setActiveIndex(index)}
                role="option"
                type="button"
              >
                <span className={cn("grid h-8 w-8 place-items-center rounded-sm border text-xs font-black", groupTone(item.group))} aria-hidden>
                  {item.group.slice(0, 1)}
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-semibold text-foreground">{item.title}</span>
                  <span className="mt-0.5 block truncate text-xs">{item.subtitle}</span>
                </span>
                <Badge tone={item.tone ?? "neutral"}>{item.group}</Badge>
              </button>
            ))}
          </div>
          <div className="flex flex-wrap justify-between gap-2 border-t border-border/70 pt-3 text-micro uppercase text-muted-foreground">
            <span>Enter to open</span>
            <span>Up/down to move</span>
            <span>Esc to close</span>
          </div>
        </div>
      </Dialog>

      <Dialog open={helpOpen} title="Keyboard Shortcuts" onClose={() => setHelpOpen(false)}>
        <div className="space-y-2">
          {HELP_ITEMS.map(([keys, label]) => (
            <div className="grid grid-cols-[132px_1fr] items-center gap-3 rounded-md border border-border/70 bg-black/18 p-2 text-sm" key={keys}>
              <kbd className="rounded-sm border border-border bg-black/24 px-2 py-1 text-center font-mono text-xs text-cyan-100">{keys}</kbd>
              <span className="text-muted-foreground">{label}</span>
            </div>
          ))}
        </div>
      </Dialog>
    </>
  );
}

function command(id: string, group: CommandGroup, title: string, subtitle: string, href: string, keywords: string[], tone?: CommandItem["tone"]): CommandItem {
  return { id, group, title, subtitle, href, keywords, tone };
}

function marketCommands(markets: MarketView[], dashboardMarkets: DashboardMarketCard[], watchlist: WatchlistItem[]): CommandItem[] {
  const dashboards = new Map(dashboardMarkets.map((market) => [market.marketSymbol, market]));
  const favoriteSymbols = new Set(watchlist.map((item) => item.marketSymbol));
  return markets.map((market) => {
    const dashboard = dashboards.get(market.symbol);
    const price = dashboard?.currentPrice === null || dashboard?.currentPrice === undefined ? "No price" : formatAmount(dashboard.currentPrice, 2);
    return {
      id: `market-${market.symbol}`,
      group: "Market",
      title: market.symbol,
      subtitle: `${market.baseAsset}/${market.quoteAsset} ${price}${favoriteSymbols.has(market.symbol) ? " Favorite" : ""}`,
      href: `/trade?symbol=${encodeURIComponent(market.symbol)}`,
      keywords: [market.symbol, market.baseAsset, market.quoteAsset, market.source, favoriteSymbols.has(market.symbol) ? "favorite watchlist" : ""],
      tone: market.enabled ? "success" : "warning"
    };
  });
}

function portfolioCommands(assets: PortfolioAsset[]): CommandItem[] {
  return assets.map((asset) => ({
    id: `portfolio-${asset.asset}`,
    group: "Portfolio",
    title: `${asset.asset} balance`,
    subtitle: `${formatAmount(asset.total)} total, ${formatAmount(asset.marketValue, 2)} value`,
    href: "/dashboard",
    keywords: [asset.asset, "portfolio", "balance", "asset"],
    tone: asset.marketValue && asset.marketValue > 0 ? "success" : "neutral"
  }));
}

function orderCommands(orders: OrderView[]): CommandItem[] {
  const unique = new Map<string, OrderView>();
  orders.forEach((order) => unique.set(order.id, order));
  return [...unique.values()].slice(0, 80).map((order) => ({
    id: `order-${order.id}`,
    group: "Order",
    title: `${order.side} ${order.marketSymbol}`,
    subtitle: `${order.status} ${formatAmount(order.quantity)} at ${formatAmount(order.limitPrice, 2)} updated ${shortDate(order.updatedAt)}`,
    href: order.remainingQuantity > 0 ? "/orders/open" : "/orders/history",
    keywords: [order.id, order.clientOrderId, order.marketSymbol, order.side, order.status, order.orderType],
    tone: order.status === "REJECTED" ? "danger" : order.status === "FILLED" ? "success" : "info"
  }));
}

function tradeCommands(trades: TradeRecord[]): CommandItem[] {
  return trades.slice(0, 80).map((trade) => ({
    id: `trade-${trade.executionId}`,
    group: "Order",
    title: `${trade.side} fill ${trade.market}`,
    subtitle: `${formatAmount(trade.quantity)} at ${formatAmount(trade.price, 2)} fee ${formatAmount(trade.fee)}`,
    href: "/trades/history",
    keywords: [trade.executionId, trade.market, trade.side, "trade", "fill", "execution"],
    tone: "success"
  }));
}

function alertCommands(alerts: PriceAlert[]): CommandItem[] {
  return alerts.map((alert) => ({
    id: `alert-${alert.id}`,
    group: "Alert",
    title: `${alert.marketSymbol} ${humanize(alert.conditionType)}`,
    subtitle: `${formatAmount(alert.threshold, 4)} ${alert.enabled ? "enabled" : "disabled"}${alert.repeating ? ", repeating" : ""}`,
    href: "/settings#price-alerts",
    keywords: [alert.marketSymbol, alert.conditionType, "price alert", alert.enabled ? "enabled" : "disabled"],
    tone: alert.enabled ? "info" : "neutral"
  }));
}

function rankCommands(items: CommandItem[], rawQuery: string): CommandItem[] {
  const normalized = normalize(rawQuery);
  if (!normalized) return items;
  return items
    .map((item) => ({ item, score: scoreItem(item, normalized) }))
    .filter((entry) => entry.score > 0)
    .sort((left, right) => right.score - left.score || left.item.title.localeCompare(right.item.title))
    .map((entry) => entry.item);
}

function scoreItem(item: CommandItem, query: string) {
  const title = normalize(item.title);
  const subtitle = normalize(item.subtitle);
  const keywords = normalize(item.keywords.join(" "));
  const haystack = `${title} ${subtitle} ${keywords}`;
  if (title === query) return 120;
  if (title.startsWith(query)) return 100;
  if (title.includes(query)) return 80;
  if (keywords.includes(query)) return 62;
  if (subtitle.includes(query)) return 45;
  return fuzzyScore(haystack, query);
}

function fuzzyScore(haystack: string, query: string) {
  let score = 0;
  let position = 0;
  for (const character of query) {
    const found = haystack.indexOf(character, position);
    if (found < 0) return 0;
    score += found === position ? 8 : 3;
    position = found + 1;
  }
  return score;
}

function normalize(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll("_", " ");
}

function groupTone(group: CommandGroup) {
  switch (group) {
    case "Market":
      return "border-emerald-300/25 bg-emerald-300/10 text-emerald-200";
    case "Order":
      return "border-cyan-300/25 bg-cyan-300/10 text-cyan-100";
    case "Admin":
      return "border-amber-300/25 bg-amber-300/10 text-amber-100";
    case "Alert":
      return "border-purple-300/25 bg-purple-300/10 text-purple-100";
    default:
      return "border-border bg-white/6 text-muted-foreground";
  }
}

function connectionTone(queries: Array<{ isFetching: boolean; isError: boolean }>): "neutral" | "warning" | "danger" | "success" {
  if (queries.some((query) => query.isError)) return "danger";
  if (queries.some((query) => query.isFetching)) return "warning";
  return "success";
}

function connectionLabel(queries: Array<{ isFetching: boolean; isError: boolean }>) {
  if (queries.some((query) => query.isError)) return "Partial";
  if (queries.some((query) => query.isFetching)) return "Syncing";
  return "Live";
}

function isTypingTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === "input" || tag === "textarea" || tag === "select" || target.isContentEditable;
}

type StoredRecent = {
  id: string;
  title: string;
};

function rememberCommand(recentKey: string, item: CommandItem) {
  if (typeof window === "undefined") return;
  try {
    const stored = window.localStorage.getItem(recentKey);
    const current = stored ? (JSON.parse(stored) as StoredRecent[]) : [];
    const next = [{ id: item.id, title: item.title }, ...current.filter((recent) => recent.id !== item.id)].slice(0, 8);
    window.localStorage.setItem(recentKey, JSON.stringify(next));
  } catch {
    // Search still works if the browser blocks local storage.
  }
}
