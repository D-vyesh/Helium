"use client";

import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, FieldError, LoadingState } from "@/components/ui/state";
import { orderEntrySchema } from "@/features/auth/schemas";
import { AssetList } from "@/features/wallet/components/wallet-panels";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { shortDate } from "@/lib/utils/format";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import type { z } from "zod";

type OrderEntryValues = z.infer<typeof orderEntrySchema>;

export function OrderEntryForm({ market }: Readonly<{ market: string }>) {
  const queryClient = useQueryClient();
  const form = useForm<OrderEntryValues>({
    resolver: zodResolver(orderEntrySchema),
    defaultValues: { market, side: "BUY", type: "LIMIT", price: "", quantity: "" }
  });
  const mutation = useMutation({
    mutationFn: heliumApi.placeOrder,
    onMutate: async (order) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.orders });
      const previous = queryClient.getQueryData(queryKeys.orders);
      queryClient.setQueryData(queryKeys.orders, (current: unknown) => Array.isArray(current) ? [{ id: "optimistic", filled: "0", status: "OPEN", createdAt: new Date().toISOString(), ...order }, ...current] : current);
      return { previous };
    },
    onError: (_error, _order, context) => queryClient.setQueryData(queryKeys.orders, context?.previous),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: queryKeys.orders })
  });

  return (
    <form className="rounded border border-slate-800 bg-slate-900 p-4" onSubmit={form.handleSubmit((values) => mutation.mutate({ ...values, market }))}>
      <h2 className="mb-4 text-lg font-semibold">Order Entry</h2>
      <div className="grid grid-cols-2 gap-2">
        <button className="rounded bg-emerald-400 px-3 py-2 text-sm font-semibold text-slate-950" type="button" onClick={() => form.setValue("side", "BUY")}>Buy</button>
        <button className="rounded bg-red-400 px-3 py-2 text-sm font-semibold text-slate-950" type="button" onClick={() => form.setValue("side", "SELL")}>Sell</button>
      </div>
      <div className="mt-4 space-y-3">
        <label className="block text-sm">
          Price
          <input className="mt-1 w-full rounded border border-slate-700 bg-slate-950 px-3 py-2" {...form.register("price")} />
          <FieldError message={form.formState.errors.price?.message} />
        </label>
        <label className="block text-sm">
          Quantity
          <input className="mt-1 w-full rounded border border-slate-700 bg-slate-950 px-3 py-2" {...form.register("quantity")} />
          <FieldError message={form.formState.errors.quantity?.message} />
        </label>
      </div>
      {mutation.isError ? <p className="mt-3 text-sm text-red-300">Order placement failed.</p> : null}
      <button className="mt-4 w-full rounded bg-cyan-400 px-4 py-2 text-sm font-semibold text-slate-950" disabled={mutation.isPending} type="submit">
        Place limit order
      </button>
    </form>
  );
}

export function OpenOrders() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.orders, queryFn: heliumApi.orders });
  const cancel = useMutation({
    mutationFn: heliumApi.cancelOrder,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.orders })
  });
  if (query.isLoading) return <LoadingState label="Loading orders" />;
  if (query.isError) return <ErrorState title="Could not load orders" />;
  const open = (query.data ?? []).filter((order) => ["OPEN", "PARTIALLY_FILLED"].includes(order.status));
  if (!open.length) return <EmptyState title="No open orders" />;
  return (
    <DataTable
      columns={["Market", "Side", "Price", "Quantity", "Filled", "Status", "Action"]}
      rows={open.map((order) => [
        order.market,
        order.side,
        order.price,
        order.quantity,
        order.filled,
        order.status,
        <button className="rounded border border-slate-700 px-3 py-1 text-xs" disabled={cancel.isPending} key={order.id} onClick={() => cancel.mutate(order.id)} type="button">Cancel</button>
      ])}
    />
  );
}

export function OrderHistory() {
  const query = useQuery({ queryKey: queryKeys.orders, queryFn: heliumApi.orders });
  if (query.isLoading) return <LoadingState label="Loading order history" />;
  if (query.isError) return <ErrorState title="Could not load order history" />;
  if (!query.data?.length) return <EmptyState title="No order history" />;
  return (
    <DataTable
      columns={["Market", "Side", "Type", "Price", "Quantity", "Filled", "Status", "Created"]}
      rows={query.data.map((order) => [order.market, order.side, order.type, order.price, order.quantity, order.filled, order.status, shortDate(order.createdAt)])}
    />
  );
}

export function TradeHistory() {
  const query = useQuery({ queryKey: queryKeys.trades, queryFn: heliumApi.trades });
  if (query.isLoading) return <LoadingState label="Loading trade history" />;
  if (query.isError) return <ErrorState title="Could not load trade history" />;
  if (!query.data?.length) return <EmptyState title="No trade history" />;
  return (
    <DataTable
      columns={["Market", "Side", "Price", "Quantity", "Fee", "Time"]}
      rows={query.data.map((trade) => [trade.market, trade.side, trade.price, trade.quantity, trade.fee, shortDate(trade.time)])}
    />
  );
}

export function BalancesPanel() {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Balances</h2>
      <AssetList />
    </section>
  );
}

export function PositionSummary({ market }: Readonly<{ market: string }>) {
  const query = useQuery({ queryKey: queryKeys.position(market), queryFn: () => heliumApi.position(market) });
  if (query.isLoading) return <LoadingState label="Loading position" />;
  if (query.isError) return <ErrorState title="Could not load position" />;
  const item = query.data;
  if (!item) return <EmptyState title="No position summary" />;
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Position Summary</h2>
      <div className="grid gap-3 text-sm md:grid-cols-2">
        <Metric label="Base" value={item.baseBalance} />
        <Metric label="Quote" value={item.quoteBalance} />
        <Metric label="Open buy" value={item.openBuyNotional} />
        <Metric label="Open sell" value={item.openSellQuantity} />
      </div>
    </section>
  );
}

function Metric({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded bg-slate-950 p-3">
      <p className="text-xs uppercase text-slate-500">{label}</p>
      <p className="mt-1 font-medium">{value}</p>
    </div>
  );
}
