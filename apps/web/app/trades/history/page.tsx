import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { TradeHistory } from "@/features/trading/components/trading-panels";

export default function TradeHistoryPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Trade History" />
      <TradeHistory />
    </ProtectedShell>
  );
}
