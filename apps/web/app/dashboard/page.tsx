import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { MarketList } from "@/features/market-data/components/market-data-panels";
import { OpenOrders, TradeHistory } from "@/features/trading/components/trading-panels";
import { BalanceSummary, WithdrawalHistory } from "@/features/wallet/components/wallet-panels";

export default function DashboardPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Dashboard" detail="Closed-beta operational view for balances, market activity, and outstanding actions." />
      <div className="space-y-6">
        <BalanceSummary />
        <section className="space-y-3">
          <h2 className="text-sm font-semibold text-muted-foreground">Markets</h2>
          <MarketList />
        </section>
        <section className="grid gap-6 xl:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Open Orders</CardTitle></CardHeader>
            <CardContent>
            <OpenOrders />
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Pending Withdrawals</CardTitle></CardHeader>
            <CardContent>
            <WithdrawalHistory pendingOnly />
            </CardContent>
          </Card>
        </section>
        <Card>
          <CardHeader><CardTitle>Recent Fills</CardTitle></CardHeader>
          <CardContent>
          <TradeHistory />
          </CardContent>
        </Card>
      </div>
    </ProtectedShell>
  );
}
