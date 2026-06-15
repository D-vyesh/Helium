import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { OpenOrders } from "@/features/trading/components/trading-panels";

export default function OpenOrdersPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Open Orders" />
      <OpenOrders />
    </ProtectedShell>
  );
}
