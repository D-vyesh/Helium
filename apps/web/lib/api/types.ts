/**
 * Frontend contracts mirroring the HELIUM backend DTOs 1:1.
 *
 * Sources of truth:
 * - AuthApiController (auth records)
 * - ApiReadService (UserDto, BalanceDto, DepositDto, WithdrawalDto, AddressDto,
 *   TradeDto, AuditDto, ReconciliationDto, ReconciliationDiscrepancyDto, AdminMarketDto)
 * - TradingApiController / OrderQueryPort.OrderView
 * - MarketDataApiController (TickerResponse, CandleResponse, TradeResponse)
 * - MarketQueryPort.MarketView, OrderBookQueryPort.OrderBookView
 * - WalletApiController / WithdrawalView
 *
 * Notes: Jackson serializes BigDecimal as JSON number, Instant as ISO string,
 * UUID as string, Set<Role> as string array.
 */

// ---- Enums (mirror backend) ----

export type UserRole =
  | "USER"
  | "ADMIN"
  | "TREASURY_ADMIN"
  | "SECURITY_ADMIN"
  | "COMPLIANCE_OFFICER"
  | "SUPPORT_AGENT"
  | "AUDITOR"
  | "RISK_MANAGER";

export type UserAccountStatus = "EMAIL_UNVERIFIED" | "ACTIVE" | "LOCKED" | "SUSPENDED" | "CLOSED";

export type OrderSide = "BUY" | "SELL";
export type OrderType = "MARKET" | "LIMIT";
export type TimeInForce = "GTC" | "IOC" | "FOK" | "DAY";

export type OrderStatus =
  | "RECEIVED"
  | "VALIDATED"
  | "FUNDS_RESERVED"
  | "SENT_TO_MATCHING"
  | "OPEN"
  | "PARTIALLY_FILLED"
  | "FILLED"
  | "CANCEL_REQUESTED"
  | "CANCELLED"
  | "EXPIRED"
  | "REJECTED";

export type DepositStatus = "DETECTED" | "CONFIRMED" | "POSTED" | "REJECTED";
export type WithdrawalStatus =
  | "REQUESTED"
  | "APPROVED"
  | "WAITING_SIGNER"
  | "SIGNED"
  | "WAITING_BROADCAST"
  | "BROADCASTING"
  | "BROADCAST_FAILED"
  | "BROADCASTED"
  | "CONFIRMING"
  | "CONFIRMATION_FAILED"
  | "REORG_DETECTED"
  | "PENDING_CONFIRMATIONS"
  | "REJECTED"
  | "CONFIRMED";

// ---- Auth (AuthApiController) ----

/** ApiReadService.UserDto */
export type SessionUser = {
  id: string;
  email: string;
  displayName: string;
  status: UserAccountStatus | string;
  emailVerified: boolean;
  mfaEnabled: boolean;
  roles: string[];
  createdAt: string;
};

export type AuthSession = {
  id: string;
  deviceName: string;
  browser: string;
  ipAddress: string;
  userAgent: string;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  status: "ACTIVE" | "REVOKED" | "EXPIRED" | string;
  current: boolean;
};

/** AuthApiController.LoginResponse */
export type LoginResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  sessionToken: string;
  refreshTokenExpiresAt: string;
  user: SessionUser;
  roles: string[];
};

/** AuthApiController.RegistrationResponse — token is NEVER returned */
export type RegistrationResponse = {
  userId: string;
  emailVerificationRequired: boolean;
};

/** AuthApiController.TokenResponse */
export type TokenResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  roles: string[];
};

export type PasswordResetResponse = { accepted: boolean };
export type PasswordResetConfirmResponse = { success: boolean };
export type EmailVerificationResponse = { verified: boolean };

/** Returned by /auth/login when MFA is required */
export type MfaChallengeResponse = { mfaSessionToken: string };

/** TOTP setup */
export type TotpSetupResponse = { secret: string; otpAuthUrl: string; qrCodeDataUrl: string };
export type TotpConfirmResponse = { enabled: boolean; backupCodes: string[] };
export type TotpDisableResponse = { disabled: boolean };
export type BackupCodesResponse = { codes: string[] };

// ---- Wallet (WalletApiController / ApiReadService) ----

/** ApiReadService.BalanceDto */
export type Balance = {
  asset: string;
  available: number;
  locked: number;
};

/** ApiReadService.AddressDto */
export type DepositAddress = {
  id: string;
  asset: string;
  network: string;
  address: string;
  memo: string | null;
  status: string;
  createdAt: string;
  paymentUri: string;
  qrCodeDataUrl: string;
};

/** ApiReadService.DepositDto */
export type DepositRecord = {
  id: string;
  asset: string;
  network: string;
  txHash: string;
  outputIndex: number;
  amount: number;
  confirmations: number;
  status: DepositStatus | string;
  createdAt: string;
};

/** ApiReadService.WithdrawalDto */
export type WithdrawalRecord = {
  id: string;
  clientRequestId: string;
  userId: string;
  asset: string;
  network: string;
  destination: string;
  memo: string | null;
  amount: number;
  fee: number | null;
  status: WithdrawalStatus | string;
  createdAt: string;
  txHash: string | null;
};

/** WalletApiController.WithdrawalRequest (request body) */
export type WithdrawalRequestBody = {
  clientRequestId: string;
  asset: string;
  network: string;
  destination: string;
  memo?: string;
  amount: string;
};

/** wallet WithdrawalView (response of POST /wallet/withdrawals) */
export type WithdrawalView = {
  withdrawalId: string;
  clientRequestId: string;
  userId: string;
  assetCode: string;
  networkCode: string;
  amount: number;
  fee: number | null;
  status: WithdrawalStatus | string;
  broadcastTxHash: string | null;
};

// ---- Markets (MarketDataApiController / MarketQueryPort) ----

/** MarketQueryPort.MarketView */
export type MarketView = {
  symbol: string;
  baseAsset: string;
  quoteAsset: string;
  priceScale: number;
  quantityScale: number;
  minOrderQuantity: number;
  minNotional: number;
  enabled: boolean;
  source: string;
};

/** MarketDataApiController.TickerResponse */
export type TickerResponse = {
  market: string;
  lastPrice: number;
  openPrice24h: number;
  highPrice24h: number;
  lowPrice24h: number;
  volume24h: number;
  quoteVolume24h: number;
  tradeCount24h: number;
  bestBid: number;
  bestAsk: number;
  spread: number;
  enabled: boolean;
  updatedAt: string | null;
};

/** MarketDataApiController.MarketStatsResponse */
export type MarketStatsResponse = {
  market: string;
  priceChange: number;
  priceChangePercent: number;
  weightedAveragePrice: number;
  lastPrice: number;
  highPrice24h: number;
  lowPrice24h: number;
  volume24h: number;
  quoteVolume24h: number;
  tradeCount24h: number;
  openTime: string;
  closeTime: string;
  updatedAt: string;
};

/** MarketDataApiController.CandleResponse */
export type CandleResponse = {
  market: string;
  interval: string;
  openTime: string;
  closeTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  quoteVolume: number;
  tradeCount: number;
  closed: boolean;
};

/** MarketDataApiController.TradeResponse */
export type PublicTrade = {
  executionId: string;
  market: string;
  price: number;
  quantity: number;
  quoteQuantity: number;
  buyerMaker: boolean;
  sequence: number;
  tradedAt: string;
};

/** OrderBookQueryPort.BookOrderView */
export type BookOrder = {
  orderId: string;
  price: number;
  remainingQuantity: number;
  receivedSequence: number;
};

/** OrderBookQueryPort.OrderBookView */
export type OrderBookView = {
  marketSymbol: string;
  lastUpdateId: number;
  bids: BookOrder[];
  asks: BookOrder[];
  updatedAt: string;
};

/** MarketDataApiController.StreamStatusResponse */
export type MarketStreamStatus = {
  enabled: boolean;
  connected: boolean;
  lastMessageAt: string | null;
  reconnects: number;
  droppedMessages: number;
  snapshotRebuilds: number;
  source: string;
};

// ---- Dashboard (DashboardApiController) ----

export type PortfolioAsset = {
  asset: string;
  available: number;
  locked: number;
  total: number;
  currentPrice: number | null;
  marketValue: number | null;
  allocationPercent: number;
  priceChangePercent24h: number | null;
  averageAcquisitionPrice: number | null;
  unrealizedPnl: number | null;
  dailyChange: number | null;
  priceUpdatedAt: string | null;
};

export type PortfolioResponse = {
  totalValue: number;
  dailyChange: number;
  dailyChangePercent: number | null;
  assetCount: number;
  assets: PortfolioAsset[];
};

export type DashboardMarketCard = {
  marketSymbol: string;
  baseAsset: string;
  quoteAsset: string;
  currentPrice: number | null;
  priceChangePercent24h: number | null;
  highPrice24h: number | null;
  lowPrice24h: number | null;
  volume24h: number | null;
  quoteVolume24h: number | null;
  marketStatus: string;
  bestBid: number | null;
  bestAsk: number | null;
  spread: number | null;
  updatedAt: string | null;
  miniChart: number[];
};

export type WatchlistItem = {
  marketSymbol: string;
  pinned: boolean;
  sortOrder: number;
  createdAt: string | null;
  market: DashboardMarketCard | null;
};

export type WatchlistItemBody = {
  marketSymbol: string;
  pinned: boolean;
  sortOrder: number;
};

export type ActivityItem = {
  id: string;
  category: string;
  eventType: string;
  summary: string;
  occurredAt: string;
};

export type ExchangeStatus = {
  connected: boolean;
  source: string;
  lastSynchronization: string | null;
  reconnects: number;
  droppedMessages: number;
  activeMarkets: number;
};

// ---- Notifications (NotificationApiController) ----

export type NotificationCategory = "TRADING" | "WALLET" | "SECURITY" | "ACCOUNT" | "ADMIN" | "SYSTEM" | "MARKET" | string;

export type ExchangeNotification = {
  id: string;
  userId: string;
  category: NotificationCategory;
  eventType: string;
  title: string;
  message: string;
  payload: Record<string, unknown>;
  read: boolean;
  createdAt: string;
  readAt: string | null;
};

export type NotificationUnreadCount = {
  unread: number;
};

// ---- Price Alerts (PriceAlertApiController) ----

export type PriceAlertCondition =
  | "PRICE_ABOVE"
  | "PRICE_BELOW"
  | "CROSSES_ABOVE"
  | "CROSSES_BELOW"
  | "CHANGE_PERCENT_ABOVE"
  | "VOLUME_ABOVE";

export type PriceAlert = {
  id: string;
  userId: string;
  marketSymbol: string;
  conditionType: PriceAlertCondition | string;
  threshold: number;
  repeating: boolean;
  enabled: boolean;
  deliveryInApp: boolean;
  deliveryEmail: boolean;
  deliveryPush: boolean;
  expiresAt: string | null;
  lastEvaluatedPrice: number | null;
  triggeredAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PriceAlertBody = {
  marketSymbol: string;
  conditionType: PriceAlertCondition;
  threshold: string;
  repeating: boolean;
  enabled: boolean;
  deliveryInApp: boolean;
  deliveryEmail: boolean;
  deliveryPush: boolean;
  expiresAt?: string | null;
};

// ---- User Preferences (UserPreferenceApiController) ----

export type UserPreferenceTheme = "SYSTEM" | "DARK" | "LIGHT";
export type ChartStyle = "CANDLES" | "BARS" | "LINE";
export type SidebarLayout = "EXPANDED" | "COMPACT" | "COLLAPSED";

export type UserPreferences = {
  userId: string;
  theme: UserPreferenceTheme | string;
  timezone: string;
  language: string;
  preferredFiat: string;
  chartInterval: string;
  chartStyle: ChartStyle | string;
  defaultMarket: string;
  sidebarLayout: SidebarLayout | string;
  workspaceLayout: Record<string, unknown>;
  orderDefaults: Record<string, unknown>;
  notificationPreferences: Record<string, unknown>;
  updatedAt: string;
};

export type UserPreferencesBody = Omit<UserPreferences, "userId" | "updatedAt">;

export type DashboardResponse = {
  portfolio: PortfolioResponse;
  watchlist: WatchlistItem[];
  markets: DashboardMarketCard[];
  topMovers: DashboardMarketCard[];
  activity: ActivityItem[];
  exchangeStatus: ExchangeStatus;
};

// ---- Trading (TradingApiController / OrderQueryPort) ----

/** TradingApiController.PlaceOrderRequest */
export type PlaceOrderBody = {
  clientOrderId: string;
  market: string;
  side: OrderSide;
  type: OrderType;
  timeInForce: TimeInForce;
  quantity: string;
  price: string;
};

/** TradingApiController.OrderResponse */
export type PlaceOrderResponse = { orderId: string };

export type OrderPreviewBody = {
  market: string;
  side: OrderSide;
  type: OrderType;
  timeInForce: TimeInForce;
  quantity: string;
  price: string;
};

export type OrderPreviewResponse = {
  marketSymbol: string;
  internalMarketSymbol: string;
  baseAsset: string;
  quoteAsset: string;
  side: OrderSide;
  orderType: OrderType;
  timeInForce: TimeInForce;
  quantity: number;
  limitPrice: number;
  notional: number;
  estimatedFee: number;
  feeAsset: string;
  feeRate: number;
  reserveAsset: string;
  reserveAmount: number;
  minOrderQuantity: number;
  minNotional: number;
  priceScale: number;
  quantityScale: number;
  supportedOrderTypes: OrderType[];
};

/** OrderQueryPort.OrderView */
export type OrderView = {
  id: string;
  userId: string;
  clientOrderId: string;
  marketSymbol: string;
  internalMarketSymbol: string;
  side: OrderSide;
  orderType: OrderType;
  status: OrderStatus;
  timeInForce: TimeInForce;
  quantity: number;
  limitPrice: number | null;
  filledQuantity: number;
  remainingQuantity: number;
  averageExecutionPrice: number | null;
  lastExecutionAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/** ApiReadService.TradeDto */
export type TradeRecord = {
  executionId: string;
  market: string;
  side: OrderSide | string;
  price: number;
  quantity: number;
  fee: number;
  time: string;
};

// ---- Admin (AdminApiController / ApiReadService) ----

/** ApiReadService.AuditDto */
export type AdminAuditRecord = {
  id: string;
  action: string;
  actorId: string;
  target: string;
  details: string;
  occurredAt: string;
};

/** ApiReadService.AdminMarketDto */
export type AdminMarketControl = {
  symbol: string;
  enabled: boolean;
  halted: boolean;
  makerFeeRate: number;
  takerFeeRate: number;
};

/** ApiReadService.ReconciliationDto */
export type ReconciliationReport = {
  id: string;
  type: string;
  status: string;
  scope: string;
  difference: number;
  createdAt: string;
};

/** ApiReadService.ReconciliationDiscrepancyDto */
export type ReconciliationDiscrepancy = {
  id: string;
  reportId: string;
  severity: string;
  scope: string;
  details: string;
  difference: number;
  detectedAt: string;
};
