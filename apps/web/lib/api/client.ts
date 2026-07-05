import { request } from "./http";
import { getAuthTokens } from "@/features/auth/token-store";
import type {
  AdminAuditRecord,
  AdminMarketControl,
  Balance,
  CandleResponse,
  DepositAddress,
  DepositRecord,
  EmailVerificationResponse,
  LoginResponse,
  MarketView,
  OrderBookView,
  OrderView,
  PasswordResetResponse,
  PlaceOrderBody,
  PlaceOrderResponse,
  PublicTrade,
  ReconciliationDiscrepancy,
  ReconciliationReport,
  RegistrationResponse,
  SessionUser,
  TickerResponse,
  TradeRecord,
  WithdrawalRecord,
  WithdrawalRequestBody,
  WithdrawalView
} from "./types";

const prefix = "/api/v1";

/**
 * Real HELIUM backend client. No fixtures, no fallbacks: every call hits the
 * backend and failures surface as typed errors (see lib/api/errors.ts).
 */
export const heliumApi = {
  // ---- Auth ----
  session: () => request<SessionUser>(`${prefix}/auth/session`),
  login: (body: { email: string; password: string }) =>
    request<LoginResponse>(`${prefix}/auth/login`, { method: "POST", body, anonymous: true }),
  register: (body: { email: string; displayName: string; password: string }) =>
    request<RegistrationResponse>(`${prefix}/auth/register`, { method: "POST", body, anonymous: true }),
  logout: () => {
    const tokens = getAuthTokens();
    if (!tokens?.refreshToken) {
      return Promise.resolve(undefined);
    }
    return request<void>(`${prefix}/auth/logout`, {
      method: "POST",
      body: { refreshToken: tokens.refreshToken, allSessions: false },
      skipRefresh: true
    });
  },
  requestPasswordReset: (email: string) =>
    request<PasswordResetResponse>(`${prefix}/auth/password-reset`, { method: "POST", body: { email }, anonymous: true }),
  verifyEmail: (token: string) =>
    request<EmailVerificationResponse>(`${prefix}/auth/email-verification`, { method: "POST", body: { token }, anonymous: true }),

  // ---- Wallet ----
  balances: () => request<Balance[]>(`${prefix}/wallet/balances`),
  depositAddresses: () => request<DepositAddress[]>(`${prefix}/wallet/addresses`),
  deposits: () => request<DepositRecord[]>(`${prefix}/wallet/deposits`),
  withdrawals: () => request<WithdrawalRecord[]>(`${prefix}/wallet/withdrawals`),
  requestWithdrawal: (body: WithdrawalRequestBody) =>
    request<WithdrawalView>(`${prefix}/wallet/withdrawals`, { method: "POST", body }),

  // ---- Market data (public) ----
  markets: () => request<MarketView[]>(`${prefix}/markets`, { anonymous: true }),
  ticker: (symbol: string) => request<TickerResponse>(`${prefix}/markets/${encodeURIComponent(symbol)}/ticker`, { anonymous: true }),
  orderBook: (symbol: string) => request<OrderBookView>(`${prefix}/markets/${encodeURIComponent(symbol)}/orderbook`, { anonymous: true }),
  publicTrades: (symbol: string) => request<PublicTrade[]>(`${prefix}/markets/${encodeURIComponent(symbol)}/trades`, { anonymous: true }),
  candles: (symbol: string, interval = "1m") =>
    request<CandleResponse[]>(`${prefix}/markets/${encodeURIComponent(symbol)}/candles?interval=${encodeURIComponent(interval)}`, { anonymous: true }),

  // ---- Trading ----
  placeOrder: (body: PlaceOrderBody) => request<PlaceOrderResponse>(`${prefix}/orders`, { method: "POST", body }),
  cancelOrder: (orderId: string) => request<void>(`${prefix}/orders/${encodeURIComponent(orderId)}`, { method: "DELETE" }),
  openOrders: () => request<OrderView[]>(`${prefix}/orders/open`),
  orderHistory: () => request<OrderView[]>(`${prefix}/orders/history`),
  tradeHistory: () => request<TradeRecord[]>(`${prefix}/trades/history`),

  // ---- Admin ----
  adminUsers: () => request<SessionUser[]>(`${prefix}/admin/users`),
  adminAudit: () => request<AdminAuditRecord[]>(`${prefix}/admin/audit`),
  adminMarkets: () => request<AdminMarketControl[]>(`${prefix}/admin/markets`),
  adminPendingWithdrawals: () => request<WithdrawalRecord[]>(`${prefix}/admin/withdrawals/pending`),
  reconciliationReports: () => request<ReconciliationReport[]>(`${prefix}/admin/reconciliation`),
  reconciliationDiscrepancies: () => request<ReconciliationDiscrepancy[]>(`${prefix}/admin/reconciliation/discrepancies`),
  exportReconciliationCsv: () => request<string>(`${prefix}/admin/reconciliation.csv`, { text: true })
};
