import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedRoute } from "@/components/layout/protected-route";
import { AppShell } from "@/components/layout/app-shell";
import { AdminOverview } from "@/features/admin/components/admin-overview";

export default function AdminPage() {
  return (
    <ProtectedRoute roles={["ADMIN", "FINANCE_OPS", "COMPLIANCE"]}>
      <AppShell>
        <PageHeader title="Admin" detail="Closed-beta operational workspace with role-aware access." />
        <AdminOverview />
      </AppShell>
    </ProtectedRoute>
  );
}
