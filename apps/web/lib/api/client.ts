import {
  asks,
  adminAuditRecords,
  adminMarketControls,
  adminUsers,
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
  reconciliationDiscrepancies,
  reconciliationReports,
  settingsProfile,
  tradeHistory,
  withdrawals
} from "./fixtures";
import type {
  AdminAuditRecord,
  AdminMarketControl,
  AdminUserRecord,
  AssetBalance,
  CandlePoint,
  DepositAddress,
  DepositRecord,
  MarketSummary,
  OrderBookLevel,
  OrderRecord,
  PositionSummary,
  PublicTrade,
  ReconciliationDiscrepancy,
  ReconciliationReport,
  SessionUser,
  SettingsProfile,
  TradeRecord,
  WithdrawalRecord
} from "./types";

const apiBaseUrl = process.env.NEXT_PUBLIC_HELIUM_API_BASE_URL;
const apiPrefix = "/api/v1";

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
  session: () => request<SessionUser>(`${apiPrefix}/auth/session`, betaUser),
  login: (body: { email: string; password: string }) => request<SessionUser>(`${apiPrefix}/auth/login`, { ...betaUser, email: body.email }, { method: "POST", body: JSON.stringify(body) }),
  register: (body: { email: string; password: string; displayName: string }) =>
    request<SessionUser>(`${apiPrefix}/auth/register`, { ...betaUser, email: body.email, displayName: body.displayName, emailVerified: false }, { method: "POST", body: JSON.stringify(body) }),
  logout: () => request<{ ok: true }>(`${apiPrefix}/auth/logout`, { ok: true }, { method: "POST" }),
  requestPasswordReset: (email: string) => request<{ accepted: true }>(`${apiPrefix}/auth/password-reset`, { accepted: true }, { method: "POST", body: JSON.stringify({ email }) }),
  verifyEmail: (token: string) => request<{ verified: true }>(`${apiPrefix}/auth/email-verification`, { verified: true }, { method: "POST", body: JSON.stringify({ token }) }),

  balances: () => request<AssetBalance[]>(`${apiPrefix}/wallet/balances`, balances),
  depositAddresses: () => request<DepositAddress[]>(`${apiPrefix}/wallet/addresses`, depositAddresses),
  deposits: () => request<DepositRecord[]>(`${apiPrefix}/wallet/deposits`, deposits),
  withdrawals: () => request<WithdrawalRecord[]>(`${apiPrefix}/wallet/withdrawals`, withdrawals),
  requestWithdrawal: (body: { asset: string; network: string; amount: string; destination: string }) =>
    request<WithdrawalRecord>(`${apiPrefix}/wallet/withdrawals`, { id: "wd-new", fee: "0.0002", status: "REQUESTED", createdAt: new Date().toISOString(), ...body }, { method: "POST", body: JSON.stringify(body) }),

  markets: () => request<MarketSummary[]>(`${apiPrefix}/markets`, markets),
  orderBook: (symbol: string) => request<{ bids: OrderBookLevel[]; asks: OrderBookLevel[] }>(`${apiPrefix}/markets/${symbol}/orderbook`, { bids, asks }),
  publicTrades: (symbol: string) => request<PublicTrade[]>(`${apiPrefix}/markets/${symbol}/trades`, publicTrades),
  candles: (symbol: string) => request<CandlePoint[]>(`${apiPrefix}/markets/${symbol}/candles?interval=1m`, candles),
  orders: () => request<OrderRecord[]>(`${apiPrefix}/orders/history`, orders),
  trades: () => request<TradeRecord[]>(`${apiPrefix}/trades/history`, tradeHistory),
  position: (symbol: string) => request<PositionSummary>(`${apiPrefix}/trading/position/${symbol}`, { ...positionSummary, market: symbol }),
  placeOrder: (body: { market: string; side: "BUY" | "SELL"; type: "LIMIT"; price: string; quantity: string }) =>
    request<OrderRecord>(`${apiPrefix}/orders`, { id: "ord-new", filled: "0", status: "OPEN", createdAt: new Date().toISOString(), ...body }, { method: "POST", body: JSON.stringify(body) }),
  cancelOrder: (orderId: string) => request<{ cancelled: true }>(`${apiPrefix}/orders/${orderId}`, { cancelled: true }, { method: "DELETE" }),

  settings: () => request<SettingsProfile>("/settings/profile", settingsProfile),

  adminUsers: () => request<AdminUserRecord[]>(`${apiPrefix}/admin/users`, adminUsers),
  adminAudit: () => request<AdminAuditRecord[]>(`${apiPrefix}/admin/audit`, adminAuditRecords),
  adminMarketControls: () => request<AdminMarketControl[]>(`${apiPrefix}/admin/markets`, adminMarketControls),
  reconciliationReports: () => request<ReconciliationReport[]>(`${apiPrefix}/admin/reconciliation`, reconciliationReports),
  reconciliationDiscrepancies: () => request<ReconciliationDiscrepancy[]>(`${apiPrefix}/admin/reconciliation/discrepancies`, reconciliationDiscrepancies),
  exportReconciliationCsv: () => request<string>(`${apiPrefix}/admin/reconciliation.csv`, [
    "reportId,type,status,scope,difference",
    ...reconciliationReports.map((report) => `${report.id},${report.type},${report.status},${report.scope},${report.difference}`)
  ].join("\n"))
};
