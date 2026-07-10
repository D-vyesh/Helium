import { request } from "./http";
import { getAuthTokens } from "@/features/auth/token-store";
import type {
  AdminAuditRecord,
  AdminMarketControl,
  AuthSession,
  BackupCodesResponse,
  Balance,
  CandleResponse,
  DashboardResponse,
  DepositAddress,
  DepositRecord,
  EmailVerificationResponse,
  ExchangeNotification,
  LoginResponse,
  MarketStatsResponse,
  MarketStreamStatus,
  MarketView,
  MfaChallengeResponse,
  NotificationUnreadCount,
  OrderBookView,
  OrderView,
  PasswordResetConfirmResponse,
  PasswordResetResponse,
  PlaceOrderBody,
  OrderPreviewBody,
  OrderPreviewResponse,
  PlaceOrderResponse,
  PriceAlert,
  PriceAlertBody,
  PublicTrade,
  ReconciliationDiscrepancy,
  ReconciliationReport,
  RegistrationResponse,
  SessionUser,
  TickerResponse,
  TotpConfirmResponse,
  TotpDisableResponse,
  TotpSetupResponse,
  TradeRecord,
  UserPreferences,
  UserPreferencesBody,
  WatchlistItem,
  WatchlistItemBody,
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
    request<LoginResponse | MfaChallengeResponse>(`${prefix}/auth/login`, { method: "POST", body, anonymous: true }),
  signup: (body: { email: string; password: string; confirmPassword: string }) =>
    request<RegistrationResponse>(`${prefix}/auth/signup`, { method: "POST", body, anonymous: true }),
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
    request<PasswordResetResponse>(`${prefix}/auth/password-reset/request`, { method: "POST", body: { email }, anonymous: true }),
  confirmPasswordReset: (token: string, newPassword: string) =>
    request<PasswordResetConfirmResponse>(`${prefix}/auth/password-reset/confirm`, {
      method: "POST", body: { token, newPassword }, anonymous: true
    }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<PasswordResetConfirmResponse>(`${prefix}/auth/password/change`, {
      method: "POST", body: { currentPassword, newPassword }
    }),
  verifyEmail: (token: string) =>
    request<EmailVerificationResponse>(`${prefix}/auth/email-verification`, { method: "POST", body: { token }, anonymous: true }),
  resendVerification: (email?: string) =>
    request<EmailVerificationResponse>(`${prefix}/auth/email-verification/resend`, { method: "POST", body: { email }, anonymous: !getAuthTokens()?.accessToken }),

  sessions: () => {
    const tokens = getAuthTokens();
    return request<AuthSession[]>(`${prefix}/sessions`, {
      headers: tokens?.refreshToken ? { "X-Session-Token": tokens.refreshToken } : undefined
    });
  },
  revokeSession: (sessionId: string) => request<void>(`${prefix}/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
  revokeAllSessions: () => request<void>(`${prefix}/sessions/all`, { method: "DELETE" }),

  // ---- TOTP MFA ----
  totpSetup: () => request<TotpSetupResponse>(`${prefix}/auth/mfa/totp/setup`, { method: "POST", body: {} }),
  totpConfirm: (code: string) =>
    request<TotpConfirmResponse>(`${prefix}/auth/mfa/totp/confirm`, { method: "POST", body: { code } }),
  totpDisable: (code: string) =>
    request<TotpDisableResponse>(`${prefix}/auth/mfa/totp`, { method: "DELETE", body: { code } }),
  totpChallenge: (mfaSessionToken: string, code: string) =>
    request<LoginResponse>(`${prefix}/auth/mfa/totp/challenge`, { method: "POST", body: { mfaSessionToken, code }, anonymous: true }),
  totpBackupCode: (mfaSessionToken: string, backupCode: string) =>
    request<LoginResponse>(`${prefix}/auth/mfa/totp/backup`, { method: "POST", body: { mfaSessionToken, backupCode }, anonymous: true }),
  listBackupCodes: () => request<BackupCodesResponse>(`${prefix}/auth/mfa/totp/backup-codes`),
  regenerateBackupCodes: (code: string) =>
    request<BackupCodesResponse>(`${prefix}/auth/mfa/totp/backup-codes/regenerate`, { method: "POST", body: { code } }),

  // ---- Wallet ----
  balances: () => request<Balance[]>(`${prefix}/wallet/balances`),
  depositAddresses: () => request<DepositAddress[]>(`${prefix}/wallet/addresses`),
  createDepositAddress: (body: { asset: string; network: string }) =>
    request<DepositAddress>(`${prefix}/wallet/addresses`, { method: "POST", body }),
  deposits: () => request<DepositRecord[]>(`${prefix}/wallet/deposits`),
  withdrawals: () => request<WithdrawalRecord[]>(`${prefix}/wallet/withdrawals`),
  requestWithdrawal: (body: WithdrawalRequestBody) =>
    request<WithdrawalView>(`${prefix}/wallet/withdrawals`, { method: "POST", body }),

  // ---- Market data (public) ----
  markets: () => request<MarketView[]>(`${prefix}/markets`, { anonymous: true }),
  market: (symbol: string) => request<MarketView>(`${prefix}/markets/${encodeURIComponent(symbol)}`, { anonymous: true }),
  ticker: (symbol: string) => request<TickerResponse>(`${prefix}/markets/${encodeURIComponent(symbol)}/ticker`, { anonymous: true }),
  marketStats: (symbol: string) => request<MarketStatsResponse>(`${prefix}/markets/${encodeURIComponent(symbol)}/stats`, { anonymous: true }),
  orderBook: (symbol: string) => request<OrderBookView>(`${prefix}/markets/${encodeURIComponent(symbol)}/orderbook`, { anonymous: true }),
  publicTrades: (symbol: string) => request<PublicTrade[]>(`${prefix}/markets/${encodeURIComponent(symbol)}/trades`, { anonymous: true }),
  candles: (symbol: string, interval = "1m") =>
    request<CandleResponse[]>(`${prefix}/markets/${encodeURIComponent(symbol)}/candles?interval=${encodeURIComponent(interval)}`, { anonymous: true }),
  marketStreamStatus: () => request<MarketStreamStatus>(`${prefix}/markets/status`, { anonymous: true }),

  // ---- Dashboard ----
  dashboard: () => request<DashboardResponse>(`${prefix}/dashboard`),
  dashboardPortfolio: () => request<DashboardResponse["portfolio"]>(`${prefix}/dashboard/portfolio`),
  dashboardMarkets: () => request<DashboardResponse["markets"]>(`${prefix}/dashboard/markets`),
  dashboardActivity: () => request<DashboardResponse["activity"]>(`${prefix}/dashboard/activity`),
  watchlist: () => request<WatchlistItem[]>(`${prefix}/dashboard/watchlist`),
  upsertWatchlistItem: (body: WatchlistItemBody) =>
    request<WatchlistItem[]>(`${prefix}/dashboard/watchlist`, { method: "POST", body }),
  removeWatchlistItem: (symbol: string) =>
    request<WatchlistItem[]>(`${prefix}/dashboard/watchlist/${encodeURIComponent(symbol)}`, { method: "DELETE" }),

  // ---- Notifications ----
  notifications: (before?: string, limit = 50) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (before) params.set("before", before);
    return request<ExchangeNotification[]>(`${prefix}/notifications?${params.toString()}`);
  },
  notificationUnreadCount: () => request<NotificationUnreadCount>(`${prefix}/notifications/unread-count`),
  markNotificationRead: (notificationId: string) =>
    request<void>(`${prefix}/notifications/${encodeURIComponent(notificationId)}/read`, { method: "POST", body: {} }),
  markAllNotificationsRead: () => request<void>(`${prefix}/notifications/read-all`, { method: "POST", body: {} }),
  deleteNotification: (notificationId: string) =>
    request<void>(`${prefix}/notifications/${encodeURIComponent(notificationId)}`, { method: "DELETE" }),

  // ---- Price alerts ----
  priceAlerts: () => request<PriceAlert[]>(`${prefix}/price-alerts`),
  createPriceAlert: (body: PriceAlertBody) => request<PriceAlert>(`${prefix}/price-alerts`, { method: "POST", body }),
  enablePriceAlert: (alertId: string) => request<PriceAlert>(`${prefix}/price-alerts/${encodeURIComponent(alertId)}/enable`, { method: "POST", body: {} }),
  disablePriceAlert: (alertId: string) => request<PriceAlert>(`${prefix}/price-alerts/${encodeURIComponent(alertId)}/disable`, { method: "POST", body: {} }),
  deletePriceAlert: (alertId: string) => request<void>(`${prefix}/price-alerts/${encodeURIComponent(alertId)}`, { method: "DELETE" }),

  // ---- Preferences ----
  preferences: () => request<UserPreferences>(`${prefix}/preferences`),
  updatePreferences: (body: UserPreferencesBody) => request<UserPreferences>(`${prefix}/preferences`, { method: "PUT", body }),

  // ---- Trading ----
  orderPreview: (body: OrderPreviewBody) => request<OrderPreviewResponse>(`${prefix}/orders/preview`, { method: "POST", body }),
  placeOrder: (body: PlaceOrderBody) => request<PlaceOrderResponse>(`${prefix}/orders`, { method: "POST", body }),
  cancelOrder: (orderId: string) => request<void>(`${prefix}/orders/${encodeURIComponent(orderId)}`, { method: "DELETE" }),
  order: (orderId: string) => request<OrderView>(`${prefix}/orders/${encodeURIComponent(orderId)}`),
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
