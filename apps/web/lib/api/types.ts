export type UserRole = "USER" | "ADMIN" | "FINANCE_OPS" | "COMPLIANCE";

export type SessionUser = {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  roles: UserRole[];
};

export type AssetBalance = {
  asset: string;
  name: string;
  available: string;
  locked: string;
  totalUsd: string;
  depositEnabled: boolean;
  withdrawalEnabled: boolean;
};

export type DepositAddress = {
  asset: string;
  network: string;
  address: string;
  tag?: string;
};

export type DepositRecord = {
  id: string;
  asset: string;
  amount: string;
  network: string;
  txHash: string;
  confirmations: number;
  status: "DETECTED" | "CONFIRMED" | "REJECTED";
  createdAt: string;
};

export type WithdrawalRecord = {
  id: string;
  asset: string;
  amount: string;
  fee: string;
  network: string;
  destination: string;
  status: "REQUESTED" | "APPROVED" | "BROADCAST" | "CONFIRMED" | "REJECTED";
  createdAt: string;
};

export type MarketSummary = {
  symbol: string;
  baseAsset: string;
  quoteAsset: string;
  lastPrice: string;
  change24h: string;
  volume24h: string;
  enabled: boolean;
};

export type OrderBookLevel = {
  price: string;
  quantity: string;
  total: string;
};

export type PublicTrade = {
  id: string;
  market: string;
  price: string;
  quantity: string;
  side: "BUY" | "SELL";
  time: string;
};

export type CandlePoint = {
  time: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: string;
};

export type OrderRecord = {
  id: string;
  market: string;
  side: "BUY" | "SELL";
  type: "LIMIT" | "MARKET";
  price: string;
  quantity: string;
  filled: string;
  status: "OPEN" | "PARTIALLY_FILLED" | "FILLED" | "CANCELLED" | "EXPIRED" | "REJECTED";
  createdAt: string;
};

export type TradeRecord = {
  id: string;
  market: string;
  side: "BUY" | "SELL";
  price: string;
  quantity: string;
  fee: string;
  time: string;
};

export type PositionSummary = {
  market: string;
  baseBalance: string;
  quoteBalance: string;
  openBuyNotional: string;
  openSellQuantity: string;
};

export type SettingsProfile = {
  email: string;
  displayName: string;
  mfaEnabled: boolean;
  accountStatus: "ACTIVE" | "LOCKED" | "SUSPENDED" | "CLOSED";
};
