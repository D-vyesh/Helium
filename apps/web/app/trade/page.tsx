import { Suspense } from "react";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { TradingTerminal } from "@/features/trading/components/trading-terminal";

export default function TradePage() {
  return (
    <ProtectedShell>
      <Suspense fallback={<main className="grid min-h-64 place-items-center p-6 text-foreground">Loading</main>}>
        <TradingTerminal />
      </Suspense>
    </ProtectedShell>
  );
}
