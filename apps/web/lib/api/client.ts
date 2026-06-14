import {
  asks,
  balances,
  betaUser,
  bids,
  candles,
  depositAddresses,
  deposits,
  markets,
  orders,
  positionSummary,
  publicTrades,
  settingsProfile,
  tradeHistory,
  withdrawals
} from "./fixtures";
import type {
  AssetBalance,
  CandlePoint,
  DepositAddress,
  DepositRecord,
  MarketSummary,
  OrderBookLevel,
  OrderRecord,
  PositionSummary,
  PublicTrade,
  SessionUser,
  SettingsProfile,
  TradeRecord,
  WithdrawalRecord
} from "./types";

const apiBaseUrl = process.env.NEXT_PUBLIC_HELIUM_API_BASE_URL;

async function request<T>(path: string, fallback: T, init?: RequestInit): Promise<T> {
  if (!apiBaseUrl) {
    await new Promise((resolve) => setTimeout(resolve, 80));
    return fallback;
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "content-type": "application/json",
      ...init?.headers
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return (await response.json()) as T;
}

export const heliumApi = {
  session: () => request<SessionUser>("/auth/session", betaUser),
  login: (body: { email: string; password: string }) => request<SessionUser>("/auth/login", { ...betaUser, email: body.email }, { method: "POST", body: JSON.stringify(body) }),
  register: (body: { email: string; password: string; displayName: string }) =>
    request<SessionUser>("/auth/register", { ...betaUser, email: body.email, displayName: body.displayName, emailVerified: false }, { method: "POST", body: JSON.stringify(body) }),
  logout: () => request<{ ok: true }>("/auth/logout", { ok: true }, { method: "POST" }),
  requestPasswordReset: (email: string) => request<{ accepted: true }>("/auth/password-reset", { accepted: true }, { method: "POST", body: JSON.stringify({ email }) }),
  verifyEmail: (token: string) => request<{ verified: true }>("/auth/email-verification", { verified: true }, { method: "POST", body: JSON.stringify({ token }) }),

  balances: () => request<AssetBalance[]>("/wallet/balances", balances),
  depositAddresses: () => request<DepositAddress[]>("/wallet/deposit-addresses", depositAddresses),
  deposits: () => request<DepositRecord[]>("/wallet/deposits", deposits),
  withdrawals: () => request<WithdrawalRecord[]>("/wallet/withdrawals", withdrawals),
  requestWithdrawal: (body: { asset: string; network: string; amount: string; destination: string }) =>
    request<WithdrawalRecord>("/wallet/withdrawals", { id: "wd-new", fee: "0.0002", status: "REQUESTED", createdAt: new Date().toISOString(), ...body }, { method: "POST", body: JSON.stringify(body) }),

  markets: () => request<MarketSummary[]>("/markets", markets),
  orderBook: (symbol: string) => request<{ bids: OrderBookLevel[]; asks: OrderBookLevel[] }>(`/market-data/book/${symbol}`, { bids, asks }),
  publicTrades: (symbol: string) => request<PublicTrade[]>(`/market-data/trades/${symbol}`, publicTrades),
  candles: (symbol: string) => request<CandlePoint[]>(`/market-data/candles/${symbol}?interval=1m`, candles),
  orders: () => request<OrderRecord[]>("/trading/orders", orders),
  trades: () => request<TradeRecord[]>("/trading/trades", tradeHistory),
  position: (symbol: string) => request<PositionSummary>(`/trading/position/${symbol}`, { ...positionSummary, market: symbol }),
  placeOrder: (body: { market: string; side: "BUY" | "SELL"; type: "LIMIT"; price: string; quantity: string }) =>
    request<OrderRecord>("/trading/orders", { id: "ord-new", filled: "0", status: "OPEN", createdAt: new Date().toISOString(), ...body }, { method: "POST", body: JSON.stringify(body) }),
  cancelOrder: (orderId: string) => request<{ cancelled: true }>(`/trading/orders/${orderId}/cancel`, { cancelled: true }, { method: "POST" }),

  settings: () => request<SettingsProfile>("/settings/profile", settingsProfile)
};
