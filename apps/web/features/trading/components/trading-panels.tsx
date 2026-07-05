"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, FieldError, LoadingState, NotImplemented } from "@/components/ui/state";
import { orderEntrySchema } from "@/features/auth/schemas";
import { AssetList } from "@/features/wallet/components/wallet-panels";
import { heliumApi } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { queryKeys } from "@/lib/query/keys";
import { formatAmount, shortDate } from "@/lib/utils/format";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { cn } from "@/lib/utils/cn";
import type { z } from "zod";

type OrderEntryValues = z.infer<typeof orderEntrySchema>;

const OPEN_STATUSES = ["RECEIVED", "VALIDATED", "FUNDS_RESERVED", "SENT_TO_MATCHING", "OPEN", "PARTIALLY_FILLED"];

export function OrderEntryForm({ market }: Readonly<{ market: string }>) {
  const queryClient = useQueryClient();
  const form = useForm<OrderEntryValues>({
    resolver: zodResolver(orderEntrySchema),
    defaultValues: { side: "BUY", type: "LIMIT", price: "", quantity: "" }
  });
  const mutation = useMutation({
    // Backend requires clientOrderId (idempotency) and timeInForce.
    mutationFn: (values: OrderEntryValues) =>
      heliumApi.placeOrder({
        clientOrderId: crypto.randomUUID(),
        market,
        side: values.side,
        type: values.type,
        timeInForce: "GTC",
        quantity: values.quantity,
        price: values.price
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
      void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
    }
  });

  return (
    <form className="glass-panel rounded-lg p-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold">Order Entry</h2>
        <Badge tone="info">{market}</Badge>
      </div>
      <div className="grid grid-cols-2 gap-1 rounded-md border border-border bg-black/20 p-1" role="group" aria-label="Order side">
        {(["BUY", "SELL"] as const).map((side) => (
          <button
            className={cn(
              "h-9 rounded-sm text-sm font-semibold transition",
              form.watch("side") === side ? (side === "BUY" ? "bg-emerald-400 text-slate-950" : "bg-red-400 text-slate-950") : "text-muted-foreground hover:bg-white/8"
            )}
            key={side}
            type="button"
            onClick={() => form.setValue("side", side)}
          >
            {side === "BUY" ? "Buy" : "Sell"}
          </button>
        ))}
      </div>
      <div className="mt-4 space-y-3">
        <label className="block text-sm">
          Price
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 font-mono text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("price")} />
          <FieldError message={form.formState.errors.price?.message} />
        </label>
        <label className="block text-sm">
          Quantity
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 font-mono text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("quantity")} />
          <FieldError message={form.formState.errors.quantity?.message} />
        </label>
      </div>
      {mutation.isError ? <p className="mt-3 text-sm text-red-300">{errorMessage(mutation.error)}</p> : null}
      {mutation.isSuccess ? <p className="mt-3 text-sm text-emerald-300">Order {mutation.data.orderId} accepted.</p> : null}
      <Button className="mt-4 w-full" disabled={mutation.isPending} type="submit">
        {mutation.isPending ? "Placing order" : "Place limit order"}
      </Button>
    </form>
  );
}

export function OpenOrders() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.openOrders, queryFn: heliumApi.openOrders, refetchInterval: 5000 });
  const cancel = useMutation({
    mutationFn: heliumApi.cancelOrder,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.openOrders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orderHistory });
    }
  });
  if (query.isLoading) return <LoadingState label="Loading orders" />;
  if (query.isError) return <ErrorState title="Could not load orders" error={query.error} onRetry={() => void query.refetch()} />;
  const open = (query.data ?? []).filter((order) => OPEN_STATUSES.includes(order.status));
  if (!open.length) return <EmptyState title="No open orders" />;
  return (
    <>
      {cancel.isError ? <ErrorState title="Cancellation failed" error={cancel.error} /> : null}
      <DataTable
        columns={["Market", "Side", "Price", "Quantity", "Filled", "Status", "Action"]}
        rows={open.map((order) => [
          order.marketSymbol,
          order.side,
          formatAmount(order.limitPrice),
          formatAmount(order.quantity),
          formatAmount(order.filledQuantity),
          <Badge key="status" tone={order.status === "OPEN" ? "success" : "warning"}>{order.status}</Badge>,
          <Button disabled={cancel.isPending} key={order.id} onClick={() => cancel.mutate(order.id)} size="sm" type="button" variant="secondary">Cancel</Button>
        ])}
      />
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
      columns={["Market", "Side", "Type", "Price", "Quantity", "Filled", "TIF", "Status", "Created"]}
      rows={query.data.map((order) => [
        order.marketSymbol,
        order.side,
        order.orderType,
        formatAmount(order.limitPrice),
        formatAmount(order.quantity),
        formatAmount(order.filledQuantity),
        order.timeInForce,
        order.status,
        shortDate(order.createdAt)
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
  // GET /api/v1/trading/position/{symbol} does not exist on the backend.
  return <NotImplemented feature="Per-market position summary" />;
}
