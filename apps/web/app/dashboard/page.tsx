import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { ExchangeDashboard } from "@/features/dashboard/components/dashboard-panels";

export default function DashboardPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Dashboard" detail="Portfolio, markets, orders, wallet activity, and exchange health from live backend state." />
      <ExchangeDashboard />
    </ProtectedShell>
  );
}
