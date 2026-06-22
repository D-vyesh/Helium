import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AssetList, BalanceSummary, DepositAddresses, DepositHistory, WithdrawalHistory } from "@/features/wallet/components/wallet-panels";

export default function WalletPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Wallet" detail="Balances, addresses, deposits, and withdrawals are read from Wallet and Ledger projections." />
      <div className="space-y-6">
        <BalanceSummary />
        <div className="flex flex-wrap gap-2">
          <ButtonLink href="/wallet/deposit">Deposit</ButtonLink>
          <ButtonLink href="/wallet/withdraw" variant="secondary">Withdraw</ButtonLink>
        </div>
        <AssetList />
        <section className="grid gap-6 xl:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Deposit Addresses</CardTitle></CardHeader>
            <CardContent>
            <DepositAddresses />
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Pending Withdrawals</CardTitle></CardHeader>
            <CardContent>
            <WithdrawalHistory pendingOnly />
            </CardContent>
          </Card>
        </section>
        <section className="grid gap-6 xl:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>Deposit History</CardTitle></CardHeader>
            <CardContent>
            <DepositHistory />
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Withdrawal History</CardTitle></CardHeader>
            <CardContent>
            <WithdrawalHistory />
            </CardContent>
          </Card>
        </section>
      </div>
    </ProtectedShell>
  );
}
