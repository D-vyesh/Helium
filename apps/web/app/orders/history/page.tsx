import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { OrderHistory } from "@/features/trading/components/trading-panels";

export default function OrderHistoryPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Order History" />
      <OrderHistory />
    </ProtectedShell>
  );
}
