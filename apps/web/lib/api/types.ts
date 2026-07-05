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
export type WithdrawalStatus = "REQUESTED" | "APPROVED" | "REJECTED" | "BROADCASTED" | "CONFIRMED";

// ---- Auth (AuthApiController) ----

/** ApiReadService.UserDto */
export type SessionUser = {
  id: string;
  email: string;
  displayName: string;
  status: UserAccountStatus | string;
  emailVerified: boolean;
  roles: string[];
  createdAt: string;
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

/** AuthApiController.RegistrationResponse */
export type RegistrationResponse = {
  userId: string;
  emailVerificationRequired: boolean;
  verificationToken: string;
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
export type EmailVerificationResponse = { verified: boolean };

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
  enabled: boolean;
  updatedAt: string | null;
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
  bids: BookOrder[];
  asks: BookOrder[];
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

/** OrderQueryPort.OrderView */
export type OrderView = {
  id: string;
  userId: string;
  clientOrderId: string;
  marketSymbol: string;
  side: OrderSide;
  orderType: OrderType;
  status: OrderStatus;
  timeInForce: TimeInForce;
  quantity: number;
  limitPrice: number | null;
  filledQuantity: number;
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
