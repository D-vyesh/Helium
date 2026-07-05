"use client";

import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/store";
import { heliumApi } from "@/lib/api/client";
import type { AdminAuditRecord, AdminMarketControl, SessionUser, WithdrawalRecord } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { EmptyState, ErrorState, LoadingState, NotImplemented } from "@/components/ui/state";
import { DataTable } from "@/components/ui/table";
import { formatAmount } from "@/lib/utils/format";

export function AdminOverview() {
  const user = useAuthStore((state) => state.user);
  const usersQuery = useQuery({ queryKey: queryKeys.adminUsers, queryFn: heliumApi.adminUsers });
  const withdrawalsQuery = useQuery({ queryKey: queryKeys.adminPendingWithdrawals, queryFn: heliumApi.adminPendingWithdrawals });
  const marketsQuery = useQuery({ queryKey: queryKeys.adminMarketControls, queryFn: heliumApi.adminMarkets });
  const auditQuery = useQuery({ queryKey: queryKeys.adminAudit, queryFn: heliumApi.adminAudit });

  if (usersQuery.isLoading || withdrawalsQuery.isLoading || marketsQuery.isLoading || auditQuery.isLoading) {
    return <LoadingState label="Loading admin workspace" />;
  }

  const failed = [usersQuery, withdrawalsQuery, marketsQuery, auditQuery].find((query) => query.isError);
  if (failed) {
    return (
      <ErrorState
        title="Admin data unavailable"
        error={failed.error}
        onRetry={() => {
          void usersQuery.refetch();
          void withdrawalsQuery.refetch();
          void marketsQuery.refetch();
          void auditQuery.refetch();
        }}
      />
    );
  }

  const users = usersQuery.data ?? [];
  const pendingWithdrawals = withdrawalsQuery.data ?? [];
  const markets = marketsQuery.data ?? [];
  const audits = auditQuery.data ?? [];

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 lg:grid-cols-4">
        <AdminCard title="Users" value={String(users.length)} detail="All registered accounts." />
        <AdminCard title="Withdrawal Queue" value={`${pendingWithdrawals.length} pending`} detail="Requested and approved withdrawals awaiting processing." />
        <AdminCard title="Markets" value={`${markets.filter((market) => market.enabled).length}/${markets.length} live`} detail="Registered trading markets." />
        <AdminCard title="Audit Events" value={String(audits.length)} detail="Latest admin audit records." />
      </div>
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <h2 className="text-lg font-semibold">Current operator</h2>
        <p className="mt-2 text-sm text-slate-400">{user?.email}</p>
        <p className="mt-1 text-sm text-slate-400">{user?.roles.join(", ")}</p>
      </section>
      <AdminUserManagement users={users} />
      <WithdrawalApprovalQueue withdrawals={pendingWithdrawals} />
      <MarketControls markets={markets} />
      <AdminAuditViewer records={audits} />
    </div>
  );
}

function AdminCard({ title, value, detail }: Readonly<{ title: string; value: string; detail: string }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <p className="text-xs uppercase text-slate-500">{title}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
      <p className="mt-2 text-sm text-slate-400">{detail}</p>
    </section>
  );
}

function AdminUserManagement({ users }: Readonly<{ users: SessionUser[] }>) {
  const [query, setQuery] = useState("");
  const filtered = useMemo(
    () => users.filter((user) => `${user.email} ${user.displayName} ${user.status} ${user.roles.join(" ")}`.toLowerCase().includes(query.toLowerCase())),
    [query, users]
  );

  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <PanelHeader title="User management" action={<SearchBox value={query} onChange={setQuery} placeholder="Search users" />} />
      {filtered.length === 0 ? (
        <EmptyState title="No users found" detail="Adjust the search terms to inspect another account." />
      ) : (
        <DataTable
          columns={["Email", "Status", "Verified", "Roles", "Created"]}
          rows={filtered.map((record) => [
            <span key="email" className="font-medium text-slate-100">{record.email}</span>,
            <StatusBadge key="status" value={record.status} />,
            <span key="verified">{record.emailVerified ? "Yes" : "No"}</span>,
            <span key="roles">{record.roles.length ? record.roles.join(", ") : "—"}</span>,
            <span key="created">{new Date(record.createdAt).toLocaleString()}</span>
          ])}
        />
      )}
    </section>
  );
}

function WithdrawalApprovalQueue({ withdrawals }: Readonly<{ withdrawals: WithdrawalRecord[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <PanelHeader title="Withdrawal approval queue" />
      {withdrawals.length === 0 ? (
        <EmptyState title="No pending withdrawals" detail="New requests will appear here for finance review." />
      ) : (
        <DataTable
          columns={["Asset", "Amount", "Network", "Destination", "Status"]}
          rows={withdrawals.map((withdrawal) => [
            <span key="asset">{withdrawal.asset}</span>,
            <span key="amount">{formatAmount(withdrawal.amount)}</span>,
            <span key="network">{withdrawal.network}</span>,
            <span key="destination" className="font-mono text-xs">{withdrawal.destination}</span>,
            <StatusBadge key="status" value={String(withdrawal.status)} />
          ])}
        />
      )}
      <div className="mt-3">
        <NotImplemented feature="Approving or rejecting withdrawals from this screen" />
      </div>
    </section>
  );
}

function MarketControls({ markets }: Readonly<{ markets: AdminMarketControl[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <PanelHeader title="Trading market controls" />
      {markets.length === 0 ? (
        <EmptyState title="No markets registered" />
      ) : (
        <DataTable
          columns={["Market", "State", "Maker fee", "Taker fee"]}
          rows={markets.map((market) => [
            <span key="symbol" className="font-medium text-slate-100">{market.symbol}</span>,
            <StatusBadge key="state" value={market.halted ? "HALTED" : market.enabled ? "LIVE" : "DISABLED"} />,
            <span key="maker">{formatAmount(market.makerFeeRate)}</span>,
            <span key="taker">{formatAmount(market.takerFeeRate)}</span>
          ])}
        />
      )}
    </section>
  );
}

function AdminAuditViewer({ records }: Readonly<{ records: AdminAuditRecord[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <PanelHeader title="Audit event viewer" />
      {records.length === 0 ? (
        <EmptyState title="No audit events" />
      ) : (
        <DataTable
          columns={["Action", "Actor", "Target", "Details", "Time"]}
          rows={records.map((record) => [
            <span key="action">{record.action}</span>,
            <span key="actor" className="font-mono text-xs">{record.actorId}</span>,
            <span key="target">{record.target}</span>,
            <span key="details">{record.details}</span>,
            <span key="time">{new Date(record.occurredAt).toLocaleString()}</span>
          ])}
        />
      )}
    </section>
  );
}

function PanelHeader({ title, action }: Readonly<{ title: string; action?: ReactNode }>) {
  return (
    <div className="mb-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <h2 className="text-lg font-semibold">{title}</h2>
      {action}
    </div>
  );
}

function SearchBox({ value, onChange, placeholder }: Readonly<{ value: string; onChange: (value: string) => void; placeholder: string }>) {
  return (
    <input
      className="h-10 rounded border border-slate-700 bg-slate-950 px-3 text-sm outline-none focus:border-cyan-400"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
    />
  );
}

function StatusBadge({ value }: Readonly<{ value: string }>) {
  const clean = value.replaceAll("_", " ");
  return <span className="inline-flex rounded border border-slate-700 px-2 py-1 text-xs uppercase text-slate-300">{clean}</span>;
}
