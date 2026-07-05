import { AppShell, PageHeader } from "@/components/layout/app-shell";
import { ProtectedRoute } from "@/components/layout/protected-route";
import { ReconciliationDashboard } from "@/features/admin/components/reconciliation-dashboard";

export default function ReconciliationPage() {
  return (
    <ProtectedRoute roles={["ADMIN", "TREASURY_ADMIN", "SECURITY_ADMIN", "COMPLIANCE_OFFICER", "AUDITOR", "RISK_MANAGER"]}>
      <AppShell>
        <PageHeader title="Reconciliation" detail="Ledger, wallet, chain, trading, and matching operational controls." />
        <ReconciliationDashboard />
      </AppShell>
    </ProtectedRoute>
  );
}
