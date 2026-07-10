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
import Image from "next/image";
import { useState } from "react";
import { useForm } from "react-hook-form";
import type { z } from "zod";

type WithdrawalValues = z.infer<typeof withdrawalSchema>;

const PENDING_WITHDRAWAL_STATUSES = [
  "REQUESTED",
  "APPROVED",
  "WAITING_SIGNER",
  "SIGNED",
  "WAITING_BROADCAST",
  "BROADCASTING",
  "BROADCAST_FAILED",
  "BROADCASTED",
  "CONFIRMING",
  "CONFIRMATION_FAILED",
  "REORG_DETECTED",
  "PENDING_CONFIRMATIONS"
];
const DEPOSIT_NETWORKS = [
  { asset: "BTC", network: "BTC", label: "Bitcoin" },
  { asset: "ETH", network: "ETH", label: "Ethereum" },
  { asset: "SOL", network: "SOL", label: "Solana" }
];

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
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState<string | null>(null);
  const query = useQuery({ queryKey: queryKeys.depositAddresses, queryFn: heliumApi.depositAddresses });
  const create = useMutation({
    mutationFn: heliumApi.createDepositAddress,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.depositAddresses });
    }
  });
  const copy = async (value: string, label: string) => {
    await navigator.clipboard.writeText(value);
    setCopied(label);
    window.setTimeout(() => setCopied(null), 1800);
  };

  if (query.isLoading) return <LoadingState label="Loading addresses" />;
  if (query.isError) return <ErrorState title="Could not load deposit addresses" error={query.error} onRetry={() => void query.refetch()} />;
  const addresses = query.data ?? [];
  return (
    <section className="space-y-4">
      <div className="grid gap-3 md:grid-cols-3">
        {DEPOSIT_NETWORKS.map((item) => {
          const exists = addresses.some((address) => address.asset === item.asset && address.network === item.network);
          return (
            <button
              className="rounded-md border border-border bg-white/6 p-3 text-left text-sm transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={create.isPending || exists}
              key={item.network}
              onClick={() => create.mutate({ asset: item.asset, network: item.network })}
              type="button"
            >
              <span className="block font-semibold text-foreground">{item.asset}</span>
              <span className="mt-1 block text-xs text-muted-foreground">{exists ? "Address assigned" : `Generate ${item.label} address`}</span>
            </button>
          );
        })}
      </div>
      {create.isError ? <ErrorState title="Could not generate address" error={create.error} /> : null}
      {copied ? <p className="text-sm text-emerald-300">{copied} copied.</p> : null}
      {!addresses.length ? <EmptyState title="No deposit addresses" detail="Generate a BTC, ETH, or SOL address to receive blockchain deposits." /> : null}
      <div className="grid gap-3 lg:grid-cols-2">
        {addresses.map((address) => {
          const uri = address.paymentUri;
          return (
            <Card key={address.id}>
              <CardContent>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold">{address.asset} on {address.network}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Status: {address.status}</p>
                  </div>
                  <a className="text-xs font-semibold text-cyan-200 hover:text-cyan-100" href={explorerAddressUrl(address.network, address.address)} rel="noreferrer" target="_blank">
                    Explorer
                  </a>
                </div>
                <p className="mt-3 break-all rounded-md border border-border bg-black/22 p-3 font-mono text-xs text-slate-300">{address.address}</p>
                {address.memo ? <p className="mt-2 text-xs text-muted-foreground">Memo {address.memo}</p> : null}
                <Image
                  alt={`${address.asset} deposit address QR code`}
                  className="mt-3 h-36 w-36 rounded-md border border-border bg-white p-2"
                  height={144}
                  src={address.qrCodeDataUrl}
                  unoptimized
                  width={144}
                />
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button onClick={() => void copy(address.address, `${address.asset} address`)} size="sm" type="button" variant="secondary">Copy address</Button>
                  <Button onClick={() => void copy(uri, `${address.asset} URI`)} size="sm" type="button" variant="ghost">Copy URI</Button>
                </div>
                <p className="mt-2 break-all text-micro uppercase text-muted-foreground">{uri}</p>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </section>
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

function explorerAddressUrl(network: string, address: string) {
  if (network === "BTC") return `https://blockstream.info/address/${encodeURIComponent(address)}`;
  if (network === "ETH") return `https://etherscan.io/address/${encodeURIComponent(address)}`;
  if (network === "SOL") return `https://solscan.io/account/${encodeURIComponent(address)}`;
  return "#";
}
