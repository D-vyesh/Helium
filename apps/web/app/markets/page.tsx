import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { MarketList } from "@/features/market-data/components/market-data-panels";

export default function MarketsPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Markets" detail="Public market projections are derived from Trading and Market Data state." />
      <MarketList />
    </ProtectedShell>
  );
}
