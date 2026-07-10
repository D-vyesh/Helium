"use client";

import { PageHeader } from "@/components/layout/app-shell";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { TradingWorkspace } from "./trading-workspace";

export function TradingTerminal() {
  const params = useSearchParams();
  const preferences = useQuery({ queryKey: queryKeys.preferences, queryFn: heliumApi.preferences });
  const market = params.get("symbol") ?? preferences.data?.defaultMarket ?? "BTCUSDT";

  return (
    <>
      <PageHeader title="Trading Terminal" detail="Professional spot workspace backed by HELIUM Trading, Matching, Ledger, and live Market Data services." />
      <TradingWorkspace symbol={market} />
    </>
  );
}
