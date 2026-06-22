"use client";

import { AssetCard, PortfolioChart, TransactionTimeline, WalletBalanceCard } from "@/components/exchange/exchange-components";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, FieldError, LoadingState } from "@/components/ui/state";
import { withdrawalSchema } from "@/features/auth/schemas";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { usd } from "@/lib/utils/format";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import type { z } from "zod";

type WithdrawalValues = z.infer<typeof withdrawalSchema>;

export function BalanceSummary() {
  const query = useQuery({ queryKey: queryKeys.balances, queryFn: heliumApi.balances });
  if (query.isLoading) return <LoadingState label="Loading balances" />;
  if (query.isError) return <ErrorState title="Could not load balances" />;
  const total = query.data?.reduce((sum, item) => sum + Number(item.totalUsd), 0) ?? 0;
  const locked = query.data?.reduce((sum, item) => sum + Number(item.locked) * (item.asset === "USD" ? 1 : 0), 0) ?? 0;
  const curve = (query.data ?? []).map((item, index) => Number(item.totalUsd) + index * 180);
  return (
    <section className="grid gap-4 xl:grid-cols-[1fr_420px]">
      <div className="grid gap-4 md:grid-cols-3">
        <WalletBalanceCard detail="Marked from ledger snapshots" label="Portfolio value" value={usd(total.toFixed(2))} />
        <WalletBalanceCard detail="Enabled custody assets" label="Assets" value={`${query.data?.length ?? 0}`} />
        <WalletBalanceCard detail="Reserved for open orders" label="Locked USD" value={usd(locked.toFixed(2))} />
      </div>
      <PortfolioChart values={curve.length ? curve : [1, 1, 1]} />
    </section>
  );
}

export function AssetList() {
  const query = useQuery({ queryKey: queryKeys.balances, queryFn: heliumApi.balances });
  if (query.isLoading) return <LoadingState label="Loading assets" />;
  if (query.isError) return <ErrorState title="Could not load assets" />;
  if (!query.data?.length) return <EmptyState title="No wallet assets" />;
  return <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{query.data.map((asset) => <AssetCard asset={{ ...asset, totalUsd: usd(asset.totalUsd) }} key={asset.asset} />)}</div>;
}

export function DepositAddresses() {
  const query = useQuery({ queryKey: queryKeys.depositAddresses, queryFn: heliumApi.depositAddresses });
  if (query.isLoading) return <LoadingState label="Loading addresses" />;
  if (query.isError) return <ErrorState title="Could not load deposit addresses" />;
  if (!query.data?.length) return <EmptyState title="No deposit addresses" detail="Generate addresses from the wallet service when assets are enabled." />;
  return (
    <div className="grid gap-3 lg:grid-cols-2">
      {query.data.map((address) => (
        <Card key={`${address.asset}-${address.network}`}>
          <CardContent>
            <p className="text-sm font-semibold">{address.asset} on {address.network}</p>
            <p className="mt-2 break-all rounded-md border border-border bg-black/22 p-3 font-mono text-xs text-slate-300">{address.address}</p>
            {address.tag ? <p className="mt-2 text-xs text-muted-foreground">Tag {address.tag}</p> : null}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

export function DepositHistory() {
  const query = useQuery({ queryKey: queryKeys.deposits, queryFn: heliumApi.deposits });
  if (query.isLoading) return <LoadingState label="Loading deposits" />;
  if (query.isError) return <ErrorState title="Could not load deposits" />;
  if (!query.data?.length) return <EmptyState title="No deposits yet" />;
  return (
    <DataTable
      columns={["Asset", "Amount", "Network", "Confirmations", "Status"]}
      rows={query.data.map((deposit) => [deposit.asset, deposit.amount, deposit.network, deposit.confirmations, deposit.status])}
    />
  );
}

export function WithdrawalHistory({ pendingOnly = false }: Readonly<{ pendingOnly?: boolean }>) {
  const query = useQuery({ queryKey: queryKeys.withdrawals, queryFn: heliumApi.withdrawals });
  if (query.isLoading) return <LoadingState label="Loading withdrawals" />;
  if (query.isError) return <ErrorState title="Could not load withdrawals" />;
  const rows = (query.data ?? []).filter((withdrawal) => !pendingOnly || ["REQUESTED", "APPROVED", "BROADCAST"].includes(withdrawal.status));
  if (!rows.length) return <EmptyState title={pendingOnly ? "No pending withdrawals" : "No withdrawals yet"} />;
  if (pendingOnly) return <TransactionTimeline items={rows} />;
  return (
    <DataTable
      columns={["Asset", "Amount", "Fee", "Network", "Destination", "Status"]}
      rows={rows.map((withdrawal) => [withdrawal.asset, withdrawal.amount, withdrawal.fee, withdrawal.network, withdrawal.destination, withdrawal.status])}
    />
  );
}

export function WithdrawalForm() {
  const queryClient = useQueryClient();
  const form = useForm<WithdrawalValues>({
    resolver: zodResolver(withdrawalSchema),
    defaultValues: { asset: "BTC", network: "Bitcoin", amount: "", destination: "" }
  });
  const mutation = useMutation({
    mutationFn: heliumApi.requestWithdrawal,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.withdrawals });
      form.reset({ asset: "BTC", network: "Bitcoin", amount: "", destination: "" });
    }
  });

  return (
    <form className="glass-panel space-y-4 rounded-lg p-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
      <div className="grid gap-4 md:grid-cols-2">
        <label className="text-sm">
          Asset
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("asset")} />
          <FieldError message={form.formState.errors.asset?.message} />
        </label>
        <label className="text-sm">
          Network
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("network")} />
          <FieldError message={form.formState.errors.network?.message} />
        </label>
        <label className="text-sm">
          Amount
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("amount")} />
          <FieldError message={form.formState.errors.amount?.message} />
        </label>
        <label className="text-sm">
          Destination
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("destination")} />
          <FieldError message={form.formState.errors.destination?.message} />
        </label>
      </div>
      {mutation.isError ? <p className="text-sm text-red-300">Withdrawal request failed.</p> : null}
      {mutation.isSuccess ? <p className="text-sm text-emerald-300">Withdrawal requested.</p> : null}
      <Button disabled={mutation.isPending} type="submit">
        Request withdrawal
      </Button>
    </form>
  );
}
