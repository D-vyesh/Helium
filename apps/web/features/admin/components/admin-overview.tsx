"use client";

import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/store";
import { heliumApi } from "@/lib/api/client";
import type { AdminAuditRecord, AdminMarketControl, AdminUserRecord, WithdrawalRecord } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { DataTable } from "@/components/ui/table";

export function AdminOverview() {
  const user = useAuthStore((state) => state.user);
  const usersQuery = useQuery({ queryKey: queryKeys.adminUsers, queryFn: heliumApi.adminUsers });
  const withdrawalsQuery = useQuery({ queryKey: queryKeys.withdrawals, queryFn: heliumApi.withdrawals });
  const marketsQuery = useQuery({ queryKey: queryKeys.adminMarketControls, queryFn: heliumApi.adminMarketControls });
  const auditQuery = useQuery({ queryKey: queryKeys.adminAudit, queryFn: heliumApi.adminAudit });

  if (usersQuery.isLoading || withdrawalsQuery.isLoading || marketsQuery.isLoading || auditQuery.isLoading) {
    return <LoadingState label="Loading admin workspace" />;
  }

  if (usersQuery.isError || withdrawalsQuery.isError || marketsQuery.isError || auditQuery.isError) {
    return <ErrorState title="Admin data unavailable" detail="The admin workspace could not load operational data." />;
  }

  const users = usersQuery.data ?? [];
  const withdrawals = withdrawalsQuery.data ?? [];
  const markets = marketsQuery.data ?? [];
  const audits = auditQuery.data ?? [];
  const pendingWithdrawals = withdrawals.filter((withdrawal) => withdrawal.status === "REQUESTED" || withdrawal.status === "APPROVED");

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 lg:grid-cols-4">
        <AdminCard title="Users" value={String(users.length)} detail="Active, locked, suspended, and verification-pending accounts." />
        <AdminCard title="Withdrawal Queue" value={`${pendingWithdrawals.length} pending`} detail="Approval and rejection remain audited backend actions." />
        <AdminCard title="Markets" value={`${markets.filter((market) => market.enabled).length}/${markets.length} live`} detail="Trading halt/resume controls are role-protected." />
        <AdminCard title="Audit Events" value={String(audits.length)} detail="Immutable operator activity feed." />
      </div>
      <section className="rounded border border-slate-800 bg-slate-900 p-4 lg:col-span-3">
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

function AdminUserManagement({ users }: Readonly<{ users: AdminUserRecord[] }>) {
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
          columns={["Email", "Status", "Roles", "Created"]}
          rows={filtered.map((record) => [
            <span key="email" className="font-medium text-slate-100">{record.email}</span>,
            <StatusBadge key="status" value={record.status} />,
            <span key="roles">{record.roles.join(", ")}</span>,
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
          columns={["Asset", "Amount", "Destination", "Status"]}
          rows={withdrawals.map((withdrawal) => [
            <span key="asset">{withdrawal.asset}</span>,
            <span key="amount">{withdrawal.amount}</span>,
            <span key="destination" className="font-mono text-xs">{withdrawal.destination}</span>,
            <StatusBadge key="status" value={withdrawal.status} />
          ])}
        />
      )}
    </section>
  );
}

function MarketControls({ markets }: Readonly<{ markets: AdminMarketControl[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <PanelHeader title="Trading market controls" />
      <DataTable
        columns={["Market", "State", "Maker fee", "Taker fee"]}
        rows={markets.map((market) => [
          <span key="symbol" className="font-medium text-slate-100">{market.symbol}</span>,
          <StatusBadge key="state" value={market.halted ? "HALTED" : market.enabled ? "LIVE" : "DISABLED"} />,
          <span key="maker">{market.makerFeeRate}</span>,
          <span key="taker">{market.takerFeeRate}</span>
        ])}
      />
    </section>
  );
}

function AdminAuditViewer({ records }: Readonly<{ records: AdminAuditRecord[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <PanelHeader title="Audit event viewer" />
      <DataTable
        columns={["Action", "Actor", "Target", "Time"]}
        rows={records.map((record) => [
          <span key="action">{record.action}</span>,
          <span key="actor" className="font-mono text-xs">{record.actorId}</span>,
          <span key="target">{record.target}</span>,
          <span key="time">{new Date(record.occurredAt).toLocaleString()}</span>
        ])}
      />
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
