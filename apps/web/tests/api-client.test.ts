import { heliumApi } from "@/lib/api/client";
import type { Balance, MarketView, OrderBookView, OrderView, SessionUser, TickerResponse } from "@/lib/api/types";

async function apiClientContracts() {
  const session: SessionUser = await heliumApi.session();
  const balances: Balance[] = await heliumApi.balances();
  const markets: MarketView[] = await heliumApi.markets();
  const ticker: TickerResponse = await heliumApi.ticker("BTCUSDT");
  const book: OrderBookView = await heliumApi.orderBook("BTCUSDT");
  const orders: OrderView[] = await heliumApi.openOrders();

  return {
    sessionEmail: session.email,
    balanceCount: balances.length,
    marketCount: markets.length,
    lastPrice: ticker.lastPrice,
    bidCount: book.bids.length,
    openOrderCount: orders.length
  };
}

void apiClientContracts;
