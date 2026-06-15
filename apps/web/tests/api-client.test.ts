import { heliumApi } from "@/lib/api/client";
import type { AssetBalance, MarketSummary, SessionUser } from "@/lib/api/types";

async function apiClientContracts() {
  const session: SessionUser = await heliumApi.session();
  const balances: AssetBalance[] = await heliumApi.balances();
  const markets: MarketSummary[] = await heliumApi.markets();

  return {
    sessionEmail: session.email,
    balanceCount: balances.length,
    marketCount: markets.length
  };
}

void apiClientContracts;
