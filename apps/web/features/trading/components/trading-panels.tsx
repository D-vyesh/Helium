"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState, ErrorState, FieldError, LoadingState } from "@/components/ui/state";
import { DataTable } from "@/components/ui/table";
import { Toast } from "@/components/ui/toast";
import { orderEntrySchema } from "@/features/auth/schemas";
import { AssetList } from "@/features/wallet/components/wallet-panels";
import { heliumApi } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { Balance, OrderView } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { cn } from "@/lib/utils/cn";
import { formatAmount, shortDate } from "@/lib/utils/format";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import type { z } from "zod";

type OrderEntryValues = z.infer<typeof orderEntrySchema>;

const OPEN_STATUSES = ["RECEIVED", "VALIDATED", "FUNDS_RESERVED", "SENT_TO_MATCHING", "OPEN", "PARTIALLY_FILLED", "CANCEL_REQUESTED"];

export function OrderEntryForm({ market }: Readonly<{ market: string }>) {
  const queryClient = useQueryClient();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [toast, setToast] = useState<{ message: string; tone: "success" | "danger" } | null>(null);
  const form = useForm<OrderEntryValues>({
    resolver: zodResolver(orderEntrySchema),
    defaultValues: { side: "BUY", type: "LIMIT", price: "", quantity: "" }
  });
  const values = form.watch();
  const assets = parseMarket(market);
  const balancesQuery = useQuery({ queryKey: queryKeys.balances, queryFn: heliumApi.balances });
  const sideBalance = balanceForSide(balancesQuery.data ?? [], values.side, assets);
  const previewQuery = useQuery({
    queryKey: queryKeys.orderPreview(market, values.side, values.type, values.quantity, values.price),
    queryFn: () =>
      heliumApi.orderPreview({
        market,
        side: values.side,
        type: values.type,
        timeInForce: "GTC",
        quantity: values.quantity,
        price: values.price
      }),
    enabled: isPositiveDecimal(values.price) && isPositiveDecimal(values.quantity)
  });
  const mutation = useMutation({
    mutationFn: (submitValues: OrderEntryValues) =>
      heliumApi.placeOrder({
        clientOrderId: crypto.randomUUID(),
        market,
        side: submitValues.side,
        type: submitValues.type,
        timeInForce: "GTC",
        quantity: submitValues.quantity,
        price: submitValues.price
      }),
    onSuccess: (response) => {
      setConfirmOpen(false);
      setToast({ message: `Order ${response.orderId} accepted.`, tone: "success" });
      void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
      void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
    },
    onError: (error) => setToast({ message: errorMessage(error), tone: "danger" })
  });
  const notional = numeric(values.price) * numeric(values.quantity);
  const submitDisabled = mutation.isPending || !previewQuery.data;

  const setQuantityPercent = (percent: number) => {
    const price = numeric(values.price);
    const available = sideBalance.available * percent;
    const quantity = values.side === "BUY" ? (price > 0 ? available / price : 0) : available;
    form.setValue("quantity", decimalInput(quantity, values.side === "BUY" ? 8 : 6), { shouldValidate: true });
  };

  useEffect(() => {
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape" && confirmOpen) {
        event.preventDefault();
        setConfirmOpen(false);
        return;
      }
      if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
        event.preventDefault();
        if (confirmOpen) {
          if (!mutation.isPending && previewQuery.data) {
            mutation.mutate(form.getValues());
          }
          return;
        }
        void form.handleSubmit(() => setConfirmOpen(true))();
        return;
      }
      if (isTypingTarget(event.target) || event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      if (event.key.toLowerCase() === "b") {
        event.preventDefault();
        form.setValue("side", "BUY", { shouldValidate: true });
      }
      if (event.key.toLowerCase() === "s") {
        event.preventDefault();
        form.setValue("side", "SELL", { shouldValidate: true });
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [confirmOpen, form, mutation, previewQuery.data]);

  return (
    <>
      <form className="glass-panel rounded-lg p-4" onSubmit={form.handleSubmit(() => setConfirmOpen(true))}>
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold">Order Entry</h2>
          <Badge tone="info">{market}</Badge>
        </div>

        <div className="grid grid-cols-2 gap-1 rounded-md border border-border bg-black/20 p-1" role="group" aria-label="Order side">
          {(["BUY", "SELL"] as const).map((side) => (
            <button
              className={cn(
                "h-9 rounded-sm text-sm font-semibold transition",
                values.side === side ? (side === "BUY" ? "bg-emerald-400 text-slate-950" : "bg-red-400 text-slate-950") : "text-muted-foreground hover:bg-white/8"
              )}
              key={side}
              type="button"
              onClick={() => form.setValue("side", side, { shouldValidate: true })}
            >
              {side === "BUY" ? "Buy" : "Sell"}
            </button>
          ))}
        </div>

        <div className="mt-3 grid grid-cols-2 gap-1 rounded-md border border-border bg-black/20 p-1">
          <button className="h-8 rounded-sm bg-white/10 text-xs font-semibold text-foreground" type="button">Limit</button>
          <button className="h-8 rounded-sm text-xs font-semibold text-muted-foreground opacity-50" disabled type="button">Market</button>
        </div>
        <div className="mt-2 grid grid-cols-4 gap-1">
          {["Stop Limit", "IOC", "FOK", "Post Only"].map((mode) => (
            <button className="h-7 rounded-sm border border-border/70 bg-black/18 text-micro font-semibold uppercase text-muted-foreground opacity-50" disabled key={mode} type="button">
              {mode}
            </button>
          ))}
        </div>

        <div className="mt-4 space-y-3">
          <label className="block text-sm">
            Price
            <div className="mt-1 flex h-10 overflow-hidden rounded-md border border-border bg-black/20 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20">
              <input className="min-w-0 flex-1 bg-transparent px-3 font-mono text-sm outline-none" inputMode="decimal" {...form.register("price")} />
              <span className="grid w-16 place-items-center border-l border-border text-xs text-muted-foreground">{assets.quote}</span>
            </div>
            <FieldError message={form.formState.errors.price?.message} />
          </label>
          <label className="block text-sm">
            Quantity
            <div className="mt-1 flex h-10 overflow-hidden rounded-md border border-border bg-black/20 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20">
              <input className="min-w-0 flex-1 bg-transparent px-3 font-mono text-sm outline-none" inputMode="decimal" {...form.register("quantity")} />
              <span className="grid w-16 place-items-center border-l border-border text-xs text-muted-foreground">{assets.base}</span>
            </div>
            <FieldError message={form.formState.errors.quantity?.message} />
          </label>
        </div>

        <div className="mt-3 grid grid-cols-5 gap-1">
          {[0.25, 0.5, 0.75, 1].map((percent) => (
            <Button key={percent} onClick={() => setQuantityPercent(percent)} size="sm" type="button" variant="secondary">
              {Math.round(percent * 100)}%
            </Button>
          ))}
          <Button onClick={() => setQuantityPercent(1)} size="sm" type="button" variant="secondary">Max</Button>
        </div>

        <div className="mt-4 space-y-2 rounded-md border border-border/70 bg-black/18 p-3 text-xs">
          <MetricRow label={`Available ${sideBalance.asset}`} value={formatAmount(sideBalance.available)} />
          <MetricRow label={`Locked ${sideBalance.asset}`} value={formatAmount(sideBalance.locked)} />
          <MetricRow label="Order value" value={`${formatAmount(notional)} ${assets.quote}`} />
          <MetricRow label="Estimated fee" value={previewQuery.data ? `${formatAmount(previewQuery.data.estimatedFee)} ${previewQuery.data.feeAsset}` : "Enter valid size"} />
          <MetricRow label="Reserved" value={previewQuery.data ? `${formatAmount(previewQuery.data.reserveAmount)} ${previewQuery.data.reserveAsset}` : "Enter valid size"} />
          <MetricRow label="Estimated total" value={previewQuery.data ? `${formatAmount(previewQuery.data.reserveAmount)} ${previewQuery.data.reserveAsset}` : "Enter valid size"} />
          <p className="text-muted-foreground">Limit orders execute at the submitted price or better; market, stop, IOC, FOK, and post-only modes activate when backend support is enabled.</p>
          {previewQuery.isError ? <p className="text-red-300">{errorMessage(previewQuery.error)}</p> : null}
        </div>

        <Button className="mt-4 w-full" disabled={submitDisabled} type="submit">
          {mutation.isPending ? "Placing order" : `${values.side === "BUY" ? "Buy" : "Sell"} ${assets.base}`}
        </Button>
      </form>

      <Dialog open={confirmOpen} title="Confirm Order" onClose={() => setConfirmOpen(false)}>
        <div className="space-y-3 text-sm">
          <MetricRow label="Market" value={market} />
          <MetricRow label="Side" value={values.side} />
          <MetricRow label="Price" value={`${values.price} ${assets.quote}`} />
          <MetricRow label="Quantity" value={`${values.quantity} ${assets.base}`} />
          <MetricRow label="Order value" value={`${formatAmount(notional)} ${assets.quote}`} />
          {previewQuery.data ? (
            <>
              <MetricRow label="Estimated fee" value={`${formatAmount(previewQuery.data.estimatedFee)} ${previewQuery.data.feeAsset}`} />
              <MetricRow label="Reserve required" value={`${formatAmount(previewQuery.data.reserveAmount)} ${previewQuery.data.reserveAsset}`} />
            </>
          ) : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button onClick={() => setConfirmOpen(false)} type="button" variant="secondary">Cancel</Button>
            <Button disabled={mutation.isPending || !previewQuery.data} onClick={() => mutation.mutate(values)} type="button">
              Confirm
            </Button>
          </div>
        </div>
      </Dialog>
      <Toast message={toast?.message} tone={toast?.tone} />
    </>
  );
}

export function OpenOrders() {
  const queryClient = useQueryClient();
  const [replaceOrder, setReplaceOrder] = useState<OrderView | null>(null);
  const [replacement, setReplacement] = useState({ price: "", quantity: "" });
  const [toast, setToast] = useState<{ message: string; tone: "success" | "danger" } | null>(null);
  const query = useQuery({ queryKey: queryKeys.openOrders, queryFn: heliumApi.openOrders });
  const cancel = useMutation({
    mutationFn: heliumApi.cancelOrder,
    onSuccess: () => {
      setToast({ message: "Cancellation requested.", tone: "success" });
      void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
      void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
    },
    onError: (error) => setToast({ message: errorMessage(error), tone: "danger" })
  });
  const replace = useMutation({
    mutationFn: async () => {
      if (!replaceOrder) return undefined;
      await heliumApi.cancelOrder(replaceOrder.id);
      return heliumApi.placeOrder({
        clientOrderId: crypto.randomUUID(),
        market: replaceOrder.marketSymbol,
        side: replaceOrder.side,
        type: "LIMIT",
        timeInForce: replaceOrder.timeInForce,
        quantity: replacement.quantity,
        price: replacement.price
      });
    },
    onSuccess: () => {
      setReplaceOrder(null);
      setToast({ message: "Replacement order submitted.", tone: "success" });
      void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
      void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
    },
    onError: (error) => setToast({ message: errorMessage(error), tone: "danger" })
  });

  if (query.isLoading) return <LoadingState label="Loading orders" />;
  if (query.isError) return <ErrorState title="Could not load orders" error={query.error} onRetry={() => void query.refetch()} />;
  const open = (query.data ?? []).filter((order) => OPEN_STATUSES.includes(order.status));
  if (!open.length) return <EmptyState title="No open orders" />;

  return (
    <>
      <DataTable
        columns={["Market", "Side", "Price", "Quantity", "Filled", "Remaining", "Avg", "Status", "Action"]}
        rows={open.map((order) => [
          order.marketSymbol,
          order.side,
          formatAmount(order.limitPrice),
          formatAmount(order.quantity),
          formatAmount(order.filledQuantity),
          formatAmount(order.remainingQuantity),
          formatAmount(order.averageExecutionPrice),
          <Badge key="status" tone={order.status === "OPEN" ? "success" : "warning"}>{order.status}</Badge>,
          <div className="flex gap-2" key={order.id}>
            <Button disabled={cancel.isPending} onClick={() => cancel.mutate(order.id)} size="sm" type="button" variant="secondary">Cancel</Button>
            <Button
              disabled={cancel.isPending || replace.isPending}
              onClick={() => {
                setReplaceOrder(order);
                setReplacement({ price: String(order.limitPrice ?? ""), quantity: String(order.remainingQuantity || order.quantity) });
              }}
              size="sm"
              type="button"
              variant="secondary"
            >
              Replace
            </Button>
          </div>
        ])}
      />
      <Dialog open={Boolean(replaceOrder)} title="Replace Order" onClose={() => setReplaceOrder(null)}>
        <div className="space-y-3 text-sm">
          <MetricRow label="Existing order" value={replaceOrder ? `${replaceOrder.side} ${replaceOrder.marketSymbol}` : ""} />
          <label className="block">
            Price
            <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 font-mono text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" value={replacement.price} onChange={(event) => setReplacement((current) => ({ ...current, price: event.target.value }))} />
          </label>
          <label className="block">
            Quantity
            <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 font-mono text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" value={replacement.quantity} onChange={(event) => setReplacement((current) => ({ ...current, quantity: event.target.value }))} />
          </label>
          <div className="flex justify-end gap-2">
            <Button onClick={() => setReplaceOrder(null)} type="button" variant="secondary">Cancel</Button>
            <Button disabled={replace.isPending || !isPositiveDecimal(replacement.price) || !isPositiveDecimal(replacement.quantity)} onClick={() => replace.mutate()} type="button">Submit replacement</Button>
          </div>
        </div>
      </Dialog>
      <Toast message={toast?.message} tone={toast?.tone} />
    </>
  );
}

export function OrderHistory() {
  const query = useQuery({ queryKey: queryKeys.orderHistory, queryFn: heliumApi.orderHistory });
  if (query.isLoading) return <LoadingState label="Loading order history" />;
  if (query.isError) return <ErrorState title="Could not load order history" error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data?.length) return <EmptyState title="No order history" />;
  return (
    <DataTable
      columns={["Market", "Side", "Type", "Price", "Quantity", "Filled", "Remaining", "Avg", "TIF", "Status", "Updated"]}
      rows={query.data.map((order) => [
        order.marketSymbol,
        order.side,
        order.orderType,
        formatAmount(order.limitPrice),
        formatAmount(order.quantity),
        formatAmount(order.filledQuantity),
        formatAmount(order.remainingQuantity),
        formatAmount(order.averageExecutionPrice),
        order.timeInForce,
        order.status,
        shortDate(order.updatedAt)
      ])}
    />
  );
}

export function TradeHistory() {
  const query = useQuery({ queryKey: queryKeys.trades, queryFn: heliumApi.tradeHistory });
  if (query.isLoading) return <LoadingState label="Loading trade history" />;
  if (query.isError) return <ErrorState title="Could not load trade history" error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data?.length) return <EmptyState title="No trade history" />;
  return (
    <DataTable
      columns={["Market", "Side", "Price", "Quantity", "Fee", "Time"]}
      rows={query.data.map((trade) => [
        trade.market,
        trade.side,
        formatAmount(trade.price),
        formatAmount(trade.quantity),
        formatAmount(trade.fee),
        shortDate(trade.time)
      ])}
    />
  );
}

export function BalancesPanel() {
  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold text-muted-foreground">Balances</h2>
      <AssetList />
    </section>
  );
}

export function PositionSummary() {
  const portfolioQuery = useQuery({ queryKey: queryKeys.dashboardPortfolio, queryFn: heliumApi.dashboardPortfolio });
  const tradesQuery = useQuery({ queryKey: queryKeys.trades, queryFn: heliumApi.tradeHistory });
  if (portfolioQuery.isLoading) return <LoadingState label="Loading portfolio" />;
  if (portfolioQuery.isError) return <ErrorState title="Could not load portfolio" error={portfolioQuery.error} onRetry={() => void portfolioQuery.refetch()} />;
  const portfolio = portfolioQuery.data;
  if (!portfolio) return <EmptyState title="No portfolio data" />;
  const lockedAssets = portfolio.assets.filter((asset) => asset.locked > 0).length;
  const recentTrade = tradesQuery.data?.[0];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Portfolio</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <MetricRow label="Portfolio value" value={`${formatAmount(portfolio.totalValue, 2)} USDT`} />
        <MetricRow label="Daily change" value={`${formatAmount(portfolio.dailyChange, 2)} USDT`} />
        <MetricRow label="Assets" value={String(portfolio.assetCount)} />
        <MetricRow label="Locked assets" value={String(lockedAssets)} />
        <MetricRow label="Recent activity" value={recentTrade ? `${recentTrade.side} ${recentTrade.market} @ ${formatAmount(recentTrade.price)}` : "No trades yet"} />
      </CardContent>
    </Card>
  );
}

function MetricRow({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-mono text-slate-200">{value}</span>
    </div>
  );
}

function parseMarket(symbol: string) {
  if (symbol.endsWith("USDT")) {
    return { base: symbol.slice(0, -4), quote: "USDT" };
  }
  const [base = symbol, quote = "USDT"] = symbol.split("-");
  return { base, quote };
}

function balanceForSide(balances: Balance[], side: "BUY" | "SELL", assets: { base: string; quote: string }) {
  const asset = side === "BUY" ? assets.quote : assets.base;
  const balance = balances.find((item) => item.asset === asset);
  return { asset, available: balance?.available ?? 0, locked: balance?.locked ?? 0 };
}

function numeric(value: string | number | null | undefined) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function isTypingTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === "input" || tag === "textarea" || tag === "select" || target.isContentEditable;
}

function isPositiveDecimal(value: string) {
  return numeric(value) > 0;
}

function decimalInput(value: number, maxFractionDigits: number) {
  if (!Number.isFinite(value) || value <= 0) return "";
  return value.toFixed(maxFractionDigits).replace(/\.?0+$/, "");
}
