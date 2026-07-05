"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { ReconciliationDiscrepancy, ReconciliationReport } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { formatAmount } from "@/lib/utils/format";

export function ReconciliationDashboard() {
  const [status, setStatus] = useState("ALL");
  const [query, setQuery] = useState("");
  const [exportError, setExportError] = useState<string | null>(null);
  const reportsQuery = useQuery({ queryKey: queryKeys.reconciliationReports, queryFn: heliumApi.reconciliationReports });
  const discrepanciesQuery = useQuery({ queryKey: queryKeys.reconciliationDiscrepancies, queryFn: heliumApi.reconciliationDiscrepancies });

  const reports = useMemo(() => reportsQuery.data ?? [], [reportsQuery.data]);
  const discrepancies = useMemo(() => discrepanciesQuery.data ?? [], [discrepanciesQuery.data]);
  const statuses = useMemo(() => [...new Set(reports.map((report) => report.status))], [reports]);
  const filteredReports = useMemo(
    () => reports.filter((report) => {
      const matchesStatus = status === "ALL" || report.status === status;
      const matchesQuery = `${report.type} ${report.scope} ${report.status}`.toLowerCase().includes(query.toLowerCase());
      return matchesStatus && matchesQuery;
    }),
    [query, reports, status]
  );

  if (reportsQuery.isLoading || discrepanciesQuery.isLoading) {
    return <LoadingState label="Loading reconciliation reports" />;
  }

  const failed = [reportsQuery, discrepanciesQuery].find((item) => item.isError);
  if (failed) {
    return (
      <ErrorState
        title="Reconciliation unavailable"
        error={failed.error}
        onRetry={() => {
          void reportsQuery.refetch();
          void discrepanciesQuery.refetch();
        }}
      />
    );
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 lg:grid-cols-4">
        <Metric title="Reports" value={String(reports.length)} />
        <Metric title="Discrepancies" value={String(discrepancies.length)} />
        <Metric title="Clean" value={String(reports.filter((report) => report.status === "CLEAN").length)} />
        <Metric title="With difference" value={String(reports.filter((report) => report.difference !== 0).length)} />
      </div>
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <div className="mb-3 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <h2 className="text-lg font-semibold">Reconciliation reports</h2>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              className="h-10 rounded border border-slate-700 bg-slate-950 px-3 text-sm outline-none focus:border-cyan-400"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search scope or type"
            />
            <select
              className="h-10 rounded border border-slate-700 bg-slate-950 px-3 text-sm outline-none focus:border-cyan-400"
              value={status}
              onChange={(event) => setStatus(event.target.value)}
            >
              <option value="ALL">All statuses</option>
              {statuses.map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
            <button
              className="h-10 rounded bg-cyan-400 px-4 text-sm font-semibold text-slate-950"
              type="button"
              onClick={() => {
                setExportError(null);
                exportCsv().catch((error: unknown) => setExportError(errorMessage(error)));
              }}
            >
              Export CSV
            </button>
          </div>
        </div>
        {exportError ? <ErrorState title="CSV export failed" detail={exportError} /> : null}
        {filteredReports.length === 0 ? (
          <EmptyState title="No reports found" detail="Run the daily reconciliation on the backend or adjust filters." />
        ) : (
          <ReportTable reports={filteredReports} />
        )}
      </section>
      <DiscrepancyTable discrepancies={discrepancies} />
    </div>
  );
}

function ReportTable({ reports }: Readonly<{ reports: ReconciliationReport[] }>) {
  return (
    <DataTable
      columns={["Type", "Scope", "Status", "Difference", "Created"]}
      rows={reports.map((report) => [
        <span key="type">{report.type.replaceAll("_", " ")}</span>,
        <span key="scope" className="font-mono text-xs">{report.scope}</span>,
        <Badge key="status" value={report.status} />,
        <span key="difference" className={report.difference !== 0 ? "text-amber-300" : "text-slate-300"}>{formatAmount(report.difference)}</span>,
        <span key="created">{new Date(report.createdAt).toLocaleString()}</span>
      ])}
    />
  );
}

function DiscrepancyTable({ discrepancies }: Readonly<{ discrepancies: ReconciliationDiscrepancy[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Discrepancies</h2>
      {discrepancies.length === 0 ? (
        <EmptyState title="No discrepancies" detail="New mismatches will appear here for manual review." />
      ) : (
        <DataTable
          columns={["Severity", "Scope", "Difference", "Details", "Detected"]}
          rows={discrepancies.map((discrepancy) => [
            <Badge key="severity" value={discrepancy.severity} />,
            <span key="scope" className="font-mono text-xs">{discrepancy.scope}</span>,
            <span key="difference">{formatAmount(discrepancy.difference)}</span>,
            <span key="details">{discrepancy.details}</span>,
            <span key="detected">{new Date(discrepancy.detectedAt).toLocaleString()}</span>
          ])}
        />
      )}
    </section>
  );
}

function Metric({ title, value }: Readonly<{ title: string; value: string }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <p className="text-xs uppercase text-slate-500">{title}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
    </section>
  );
}

function Badge({ value }: Readonly<{ value: string }>) {
  return <span className="inline-flex rounded border border-slate-700 px-2 py-1 text-xs uppercase text-slate-300">{value.replaceAll("_", " ")}</span>;
}

async function exportCsv() {
  const csv = await heliumApi.exportReconciliationCsv();
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "helium-reconciliation.csv";
  link.click();
  URL.revokeObjectURL(url);
}
