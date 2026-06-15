import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { WithdrawalForm, WithdrawalHistory } from "@/features/wallet/components/wallet-panels";

export default function WithdrawPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Withdraw" detail="Withdrawal requests enter manual approval and settlement workflows." />
      <div className="space-y-6">
        <WithdrawalForm />
        <WithdrawalHistory pendingOnly />
      </div>
    </ProtectedShell>
  );
}
