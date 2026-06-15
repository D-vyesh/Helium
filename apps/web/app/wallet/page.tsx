import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { AssetList, BalanceSummary, DepositAddresses, DepositHistory, WithdrawalHistory } from "@/features/wallet/components/wallet-panels";
import Link from "next/link";

export default function WalletPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Wallet" detail="Balances, addresses, deposits, and withdrawals are read from Wallet and Ledger projections." />
      <div className="space-y-6">
        <BalanceSummary />
        <div className="flex flex-wrap gap-2">
          <Link className="rounded bg-cyan-400 px-4 py-2 text-sm font-semibold text-slate-950" href="/wallet/deposit">Deposit</Link>
          <Link className="rounded border border-slate-700 px-4 py-2 text-sm text-slate-200" href="/wallet/withdraw">Withdraw</Link>
        </div>
        <AssetList />
        <section className="grid gap-6 xl:grid-cols-2">
          <div>
            <h2 className="mb-3 text-lg font-semibold">Deposit addresses</h2>
            <DepositAddresses />
          </div>
          <div>
            <h2 className="mb-3 text-lg font-semibold">Pending withdrawals</h2>
            <WithdrawalHistory pendingOnly />
          </div>
        </section>
        <section className="grid gap-6 xl:grid-cols-2">
          <div>
            <h2 className="mb-3 text-lg font-semibold">Deposit history</h2>
            <DepositHistory />
          </div>
          <div>
            <h2 className="mb-3 text-lg font-semibold">Withdrawal history</h2>
            <WithdrawalHistory />
          </div>
        </section>
      </div>
    </ProtectedShell>
  );
}
