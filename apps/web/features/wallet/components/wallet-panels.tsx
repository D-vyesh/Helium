"use client";

import { AssetCard, TransactionTimeline, WalletBalanceCard } from "@/components/exchange/exchange-components";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, FieldError, LoadingState } from "@/components/ui/state";
import { withdrawalSchema } from "@/features/auth/schemas";
import { heliumApi } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import { queryKeys } from "@/lib/query/keys";
import { formatAmount, shortDate } from "@/lib/utils/format";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import type { z } from "zod";

type WithdrawalValues = z.infer<typeof withdrawalSchema>;

const PENDING_WITHDRAWAL_STATUSES = ["REQUESTED", "APPROVED", "BROADCASTED"];

export function BalanceSummary() {
  const query = useQuery({ queryKey: queryKeys.balances, queryFn: heliumApi.balances });
  if (query.isLoading) return <LoadingState label="Loading balances" />;
  if (query.isError) return <ErrorState title="Could not load balances" error={query.error} onRetry={() => void query.refetch()} />;
  const balances = query.data ?? [];
  const lockedAssets = balances.filter((balance) => balance.locked > 0).length;
  return (
    <section className="grid gap-4 md:grid-cols-3">
      <WalletBalanceCard detail="Ledger asset accounts" label="Assets" value={String(balances.length)} />
      <WalletBalanceCard detail="Assets with funds reserved for orders or withdrawals" label="Locked assets" value={String(lockedAssets)} />
      <WalletBalanceCard detail="Values read from ledger balance snapshots" label="Source" value="Ledger" />
    </section>
  );
}

export function AssetList() {
  const query = useQuery({ queryKey: queryKeys.balances, queryFn: heliumApi.balances });
  if (query.isLoading) return <LoadingState label="Loading assets" />;
  if (query.isError) return <ErrorState title="Could not load assets" error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data?.length) return <EmptyState title="No wallet assets" detail="Balances appear after your first deposit is credited to the ledger." />;
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {query.data.map((balance) => (
        <AssetCard balance={balance} key={balance.asset} />
      ))}
    </div>
  );
}

export function DepositAddresses() {
  const query = useQuery({ queryKey: queryKeys.depositAddresses, queryFn: heliumApi.depositAddresses });
  if (query.isLoading) return <LoadingState label="Loading addresses" />;
  if (query.isError) return <ErrorState title="Could not load deposit addresses" error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data?.length) return <EmptyState title="No deposit addresses" detail="No addresses have been assigned to this account yet." />;
  return (
    <div className="grid gap-3 lg:grid-cols-2">
      {query.data.map((address) => (
        <Card key={address.id}>
          <CardContent>
            <p className="text-sm font-semibold">{address.asset} on {address.network}</p>
            <p className="mt-2 break-all rounded-md border border-border bg-black/22 p-3 font-mono text-xs text-slate-300">{address.address}</p>
            {address.memo ? <p className="mt-2 text-xs text-muted-foreground">Memo {address.memo}</p> : null}
            <p className="mt-2 text-xs text-muted-foreground">Status: {address.status}</p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

export function DepositHistory() {
  const query = useQuery({ queryKey: queryKeys.deposits, queryFn: heliumApi.deposits });
  if (query.isLoading) return <LoadingState label="Loading deposits" />;
  if (query.isError) return <ErrorState title="Could not load deposits" error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data?.length) return <EmptyState title="No deposits yet" />;
  return (
    <DataTable
      columns={["Asset", "Amount", "Network", "Tx Hash", "Confirmations", "Status", "Detected"]}
      rows={query.data.map((deposit) => [
        deposit.asset,
        formatAmount(deposit.amount),
        deposit.network,
        <span className="font-mono text-xs" key="tx">{deposit.txHash}</span>,
        deposit.confirmations,
        deposit.status,
        shortDate(deposit.createdAt)
      ])}
    />
  );
}

export function WithdrawalHistory({ pendingOnly = false }: Readonly<{ pendingOnly?: boolean }>) {
  const query = useQuery({ queryKey: queryKeys.withdrawals, queryFn: heliumApi.withdrawals });
  if (query.isLoading) return <LoadingState label="Loading withdrawals" />;
  if (query.isError) return <ErrorState title="Could not load withdrawals" error={query.error} onRetry={() => void query.refetch()} />;
  const rows = (query.data ?? []).filter((withdrawal) => !pendingOnly || PENDING_WITHDRAWAL_STATUSES.includes(String(withdrawal.status)));
  if (!rows.length) return <EmptyState title={pendingOnly ? "No pending withdrawals" : "No withdrawals yet"} />;
  if (pendingOnly) return <TransactionTimeline items={rows} />;
  return (
    <DataTable
      columns={["Asset", "Amount", "Fee", "Network", "Destination", "Status", "Requested"]}
      rows={rows.map((withdrawal) => [
        withdrawal.asset,
        formatAmount(withdrawal.amount),
        formatAmount(withdrawal.fee),
        withdrawal.network,
        <span className="font-mono text-xs" key="dest">{withdrawal.destination}</span>,
        withdrawal.status,
        shortDate(withdrawal.createdAt)
      ])}
    />
  );
}

export function WithdrawalForm() {
  const queryClient = useQueryClient();
  const form = useForm<WithdrawalValues>({
    resolver: zodResolver(withdrawalSchema),
    defaultValues: { asset: "", network: "", amount: "", destination: "", memo: "" }
  });
  const mutation = useMutation({
    // clientRequestId is required by the backend for idempotency.
    mutationFn: (values: WithdrawalValues) =>
      heliumApi.requestWithdrawal({
        clientRequestId: crypto.randomUUID(),
        asset: values.asset,
        network: values.network,
        destination: values.destination,
        memo: values.memo || undefined,
        amount: values.amount
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.withdrawals });
      void queryClient.invalidateQueries({ queryKey: queryKeys.balances });
      form.reset({ asset: "", network: "", amount: "", destination: "", memo: "" });
    }
  });

  return (
    <form className="glass-panel space-y-4 rounded-lg p-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
      <div className="grid gap-4 md:grid-cols-2">
        <Field error={form.formState.errors.asset?.message} label="Asset (e.g. BTC)" registration={form.register("asset")} />
        <Field error={form.formState.errors.network?.message} label="Network (e.g. BTC)" registration={form.register("network")} />
        <Field error={form.formState.errors.amount?.message} label="Amount" registration={form.register("amount")} />
        <Field error={form.formState.errors.destination?.message} label="Destination address" registration={form.register("destination")} />
        <Field error={form.formState.errors.memo?.message} label="Memo (optional)" registration={form.register("memo")} />
      </div>
      {mutation.isError ? <p className="text-sm text-red-300">{errorMessage(mutation.error)}</p> : null}
      {mutation.isSuccess ? (
        <p className="text-sm text-emerald-300">
          Withdrawal {mutation.data.withdrawalId} requested with status {mutation.data.status}.
        </p>
      ) : null}
      <Button disabled={mutation.isPending} type="submit">
        {mutation.isPending ? "Submitting" : "Request withdrawal"}
      </Button>
    </form>
  );
}

function Field({
  label,
  error,
  registration
}: Readonly<{ label: string; error?: string; registration: ReturnType<ReturnType<typeof useForm<WithdrawalValues>>["register"]> }>) {
  return (
    <label className="text-sm">
      {label}
      <input
        className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
        {...registration}
      />
      <FieldError message={error} />
    </label>
  );
}
