import { ProtectedShell } from "@/components/layout/protected-shell";
import { TradingTerminal } from "@/features/trading/components/trading-terminal";

export default function TradePage() {
  return (
    <ProtectedShell>
      <TradingTerminal />
    </ProtectedShell>
  );
}
