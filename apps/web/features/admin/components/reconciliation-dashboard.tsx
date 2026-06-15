"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { DataTable } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import type { ReconciliationDiscrepancy, ReconciliationReport } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";

type StatusFilter = "ALL" | ReconciliationReport["status"];

export function ReconciliationDashboard() {
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [query, setQuery] = useState("");
  const reportsQuery = useQuery({ queryKey: queryKeys.reconciliationReports, queryFn: heliumApi.reconciliationReports });
  const discrepanciesQuery = useQuery({ queryKey: queryKeys.reconciliationDiscrepancies, queryFn: heliumApi.reconciliationDiscrepancies });

  const reports = useMemo(() => reportsQuery.data ?? [], [reportsQuery.data]);
  const discrepancies = useMemo(() => discrepanciesQuery.data ?? [], [discrepanciesQuery.data]);
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

  if (reportsQuery.isError || discrepanciesQuery.isError) {
    return <ErrorState title="Reconciliation unavailable" detail="The reconciliation workspace could not load reports." />;
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 lg:grid-cols-4">
        <Metric title="Reports" value={String(reports.length)} />
        <Metric title="Discrepancies" value={String(discrepancies.length)} />
        <Metric title="Clean" value={String(reports.filter((report) => report.status === "CLEAN").length)} />
        <Metric title="Open Review" value={String(discrepancies.filter((discrepancy) => discrepancy.status !== "RESOLVED").length)} />
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
              onChange={(event) => setStatus(event.target.value as StatusFilter)}
            >
              <option value="ALL">All statuses</option>
              <option value="CLEAN">Clean</option>
              <option value="DISCREPANCY">Discrepancy</option>
            </select>
            <button
              className="h-10 rounded bg-cyan-400 px-4 text-sm font-semibold text-slate-950"
              type="button"
              onClick={() => void exportCsv()}
            >
              Export CSV
            </button>
          </div>
        </div>
        {filteredReports.length === 0 ? <EmptyState title="No reports found" detail="Adjust filters to inspect another reconciliation scope." /> : <ReportTable reports={filteredReports} />}
      </section>
      <DiscrepancyTable discrepancies={discrepancies} />
    </div>
  );
}

function ReportTable({ reports }: Readonly<{ reports: ReconciliationReport[] }>) {
  return (
    <DataTable
      columns={["Type", "Scope", "Status", "Totals", "Difference"]}
      rows={reports.map((report) => [
        <span key="type">{report.type.replaceAll("_", " ")}</span>,
        <span key="scope" className="font-mono text-xs">{report.scope}</span>,
        <Badge key="status" value={report.status} />,
        <span key="totals">{report.leftTotal} / {report.rightTotal}</span>,
        <span key="difference" className={report.status === "DISCREPANCY" ? "text-amber-300" : "text-slate-300"}>{report.difference}</span>
      ])}
    />
  );
}

function DiscrepancyTable({ discrepancies }: Readonly<{ discrepancies: ReconciliationDiscrepancy[] }>) {
  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4">
      <h2 className="mb-3 text-lg font-semibold">Manual reconciliation workflow</h2>
      {discrepancies.length === 0 ? (
        <EmptyState title="No discrepancies" detail="New mismatches will appear here for manual review." />
      ) : (
        <DataTable
          columns={["Severity", "Scope", "Difference", "Status", "Details"]}
          rows={discrepancies.map((discrepancy) => [
            <Badge key="severity" value={discrepancy.severity} />,
            <span key="scope" className="font-mono text-xs">{discrepancy.scope}</span>,
            <span key="difference">{discrepancy.difference}</span>,
            <Badge key="status" value={discrepancy.status} />,
            <span key="details">{discrepancy.details}</span>
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
