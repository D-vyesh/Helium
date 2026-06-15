import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { MarketList } from "@/features/market-data/components/market-data-panels";
import { OpenOrders, TradeHistory } from "@/features/trading/components/trading-panels";
import { BalanceSummary, WithdrawalHistory } from "@/features/wallet/components/wallet-panels";

export default function DashboardPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Dashboard" detail="Closed-beta operational view for balances, market activity, and outstanding actions." />
      <div className="space-y-6">
        <BalanceSummary />
        <section>
          <h2 className="mb-3 text-lg font-semibold">Markets</h2>
          <MarketList />
        </section>
        <section className="grid gap-6 xl:grid-cols-2">
          <div>
            <h2 className="mb-3 text-lg font-semibold">Open Orders</h2>
            <OpenOrders />
          </div>
          <div>
            <h2 className="mb-3 text-lg font-semibold">Pending Withdrawals</h2>
            <WithdrawalHistory pendingOnly />
          </div>
        </section>
        <section>
          <h2 className="mb-3 text-lg font-semibold">Recent Fills</h2>
          <TradeHistory />
        </section>
      </div>
    </ProtectedShell>
  );
}
