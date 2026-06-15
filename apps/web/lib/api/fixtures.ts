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

export const betaUser: SessionUser = {
  id: "beta-user",
  email: "founder@helium.exchange",
  displayName: "Closed Beta User",
  emailVerified: true,
  roles: ["USER", "FINANCE_OPS"]
};

export const balances: AssetBalance[] = [
  { asset: "BTC", name: "Bitcoin", available: "0.8421", locked: "0.0500", totalUsd: "61420.12", depositEnabled: true, withdrawalEnabled: true },
  { asset: "ETH", name: "Ethereum", available: "8.2400", locked: "0.0000", totalUsd: "28412.98", depositEnabled: true, withdrawalEnabled: true },
  { asset: "USD", name: "US Dollar", available: "94250.00", locked: "6200.00", totalUsd: "100450.00", depositEnabled: false, withdrawalEnabled: false }
];

export const depositAddresses: DepositAddress[] = [
  { asset: "BTC", network: "Bitcoin", address: "bc1qheliumclosedbeta000000000000000000000" },
  { asset: "ETH", network: "Ethereum", address: "0x0000000000000000000000000000000000HeLiUm" }
];

export const deposits: DepositRecord[] = [
  { id: "dep-1", asset: "BTC", amount: "0.4200", network: "Bitcoin", txHash: "btc-tx-001", confirmations: 6, status: "CONFIRMED", createdAt: "2026-06-14T08:35:00Z" },
  { id: "dep-2", asset: "ETH", amount: "4.0000", network: "Ethereum", txHash: "eth-tx-014", confirmations: 11, status: "CONFIRMED", createdAt: "2026-06-14T07:20:00Z" }
];

export const withdrawals: WithdrawalRecord[] = [
  { id: "wd-1", asset: "BTC", amount: "0.0500", fee: "0.0002", network: "Bitcoin", destination: "bc1qexternalbeta", status: "APPROVED", createdAt: "2026-06-14T09:10:00Z" },
  { id: "wd-2", asset: "ETH", amount: "1.2000", fee: "0.0030", network: "Ethereum", destination: "0xBetaDestination", status: "REQUESTED", createdAt: "2026-06-14T10:00:00Z" }
];

export const markets: MarketSummary[] = [
  { symbol: "BTC-USD", baseAsset: "BTC", quoteAsset: "USD", lastPrice: "68750.00", change24h: "+2.14%", volume24h: "1284.42 BTC", enabled: true },
  { symbol: "ETH-USD", baseAsset: "ETH", quoteAsset: "USD", lastPrice: "3448.20", change24h: "-0.48%", volume24h: "8421.90 ETH", enabled: true },
  { symbol: "SOL-USD", baseAsset: "SOL", quoteAsset: "USD", lastPrice: "156.42", change24h: "+4.02%", volume24h: "102940 SOL", enabled: false }
];

export const bids: OrderBookLevel[] = [
  { price: "68749.50", quantity: "0.4120", total: "28324.79" },
  { price: "68748.10", quantity: "0.8810", total: "60570.07" },
  { price: "68745.00", quantity: "1.2050", total: "82837.73" }
];

export const asks: OrderBookLevel[] = [
  { price: "68750.10", quantity: "0.2250", total: "15468.77" },
  { price: "68752.00", quantity: "0.7440", total: "51151.49" },
  { price: "68755.20", quantity: "1.1000", total: "75630.72" }
];

export const publicTrades: PublicTrade[] = [
  { id: "tr-1", market: "BTC-USD", price: "68750.10", quantity: "0.041", side: "BUY", time: "10:32:12" },
  { id: "tr-2", market: "BTC-USD", price: "68749.50", quantity: "0.110", side: "SELL", time: "10:32:08" },
  { id: "tr-3", market: "BTC-USD", price: "68750.00", quantity: "0.070", side: "BUY", time: "10:31:59" }
];

export const candles: CandlePoint[] = [
  { time: "10:00", open: "68120", high: "68500", low: "68020", close: "68440", volume: "22.4" },
  { time: "10:15", open: "68440", high: "68810", low: "68380", close: "68720", volume: "18.1" },
  { time: "10:30", open: "68720", high: "68900", low: "68680", close: "68750", volume: "14.9" }
];

export const orders: OrderRecord[] = [
  { id: "ord-1", market: "BTC-USD", side: "BUY", type: "LIMIT", price: "68000.00", quantity: "0.2000", filled: "0.0000", status: "OPEN", createdAt: "2026-06-14T10:25:00Z" },
  { id: "ord-2", market: "ETH-USD", side: "SELL", type: "LIMIT", price: "3600.00", quantity: "1.0000", filled: "0.2500", status: "PARTIALLY_FILLED", createdAt: "2026-06-14T09:40:00Z" },
  { id: "ord-3", market: "BTC-USD", side: "SELL", type: "LIMIT", price: "69000.00", quantity: "0.1000", filled: "0.1000", status: "FILLED", createdAt: "2026-06-13T15:18:00Z" }
];

export const tradeHistory: TradeRecord[] = [
  { id: "fill-1", market: "BTC-USD", side: "SELL", price: "69000.00", quantity: "0.1000", fee: "6.90 USD", time: "2026-06-13T15:19:00Z" },
  { id: "fill-2", market: "ETH-USD", side: "SELL", price: "3550.00", quantity: "0.2500", fee: "0.89 USD", time: "2026-06-14T09:45:00Z" }
];

export const positionSummary: PositionSummary = {
  market: "BTC-USD",
  baseBalance: "0.8421 BTC",
  quoteBalance: "94,250.00 USD",
  openBuyNotional: "6,200.00 USD",
  openSellQuantity: "0.0500 BTC"
};

export const settingsProfile: SettingsProfile = {
  email: betaUser.email,
  displayName: betaUser.displayName,
  mfaEnabled: false,
  accountStatus: "ACTIVE"
};

export const adminUsers: AdminUserRecord[] = [
  { id: "user-1", email: "founder@helium.exchange", displayName: "Founder", status: "ACTIVE", roles: ["ADMIN", "FINANCE_OPS"], createdAt: "2026-06-01T09:00:00Z" },
  { id: "user-2", email: "ops@helium.exchange", displayName: "Finance Ops", status: "ACTIVE", roles: ["FINANCE_OPS"], createdAt: "2026-06-05T12:00:00Z" },
  { id: "user-3", email: "review@helium.exchange", displayName: "Compliance Review", status: "LOCKED", roles: ["COMPLIANCE"], createdAt: "2026-06-07T16:30:00Z" }
];

export const adminAuditRecords: AdminAuditRecord[] = [
  { id: "audit-1", action: "WITHDRAWAL_APPROVED", actorId: "user-2", target: "wd-2", details: "Manual approval for closed beta withdrawal", occurredAt: "2026-06-14T10:15:00Z" },
  { id: "audit-2", action: "RECONCILIATION_REPORT_CREATED", actorId: "user-1", target: "BTC:BITCOIN", details: "Ledger vs wallet report created", occurredAt: "2026-06-14T09:00:00Z" }
];

export const adminMarketControls: AdminMarketControl[] = [
  { symbol: "BTC-USD", enabled: true, halted: false, makerFeeRate: "0.0010", takerFeeRate: "0.0020" },
  { symbol: "ETH-USD", enabled: true, halted: false, makerFeeRate: "0.0010", takerFeeRate: "0.0020" },
  { symbol: "SOL-USD", enabled: false, halted: true, makerFeeRate: "0.0015", takerFeeRate: "0.0025" }
];

export const reconciliationReports: ReconciliationReport[] = [
  { id: "rec-1", type: "LEDGER_WALLET", status: "CLEAN", scope: "BTC:BITCOIN", leftLabel: "ledger_external", rightLabel: "wallet_posted", leftTotal: "12.84210000", rightTotal: "12.84210000", difference: "0", createdAt: "2026-06-14T09:00:00Z" },
  { id: "rec-2", type: "WALLET_CHAIN", status: "DISCREPANCY", scope: "ETH:ETHEREUM", leftLabel: "wallet_posted", rightLabel: "chain_confirmed", leftTotal: "421.00000000", rightTotal: "420.75000000", difference: "0.25000000", createdAt: "2026-06-14T09:01:00Z" },
  { id: "rec-3", type: "MATCHING_EXECUTION", status: "CLEAN", scope: "BTC-USD", leftLabel: "matching_executions", rightLabel: "trading_settlements", leftTotal: "142", rightTotal: "142", difference: "0", createdAt: "2026-06-14T09:02:00Z" }
];

export const reconciliationDiscrepancies: ReconciliationDiscrepancy[] = [
  { id: "disc-1", reportId: "rec-2", severity: "MEDIUM", scope: "ETH:ETHEREUM", details: "Wallet posted total differs from confirmed chain total.", difference: "0.25000000", status: "OPEN", detectedAt: "2026-06-14T09:01:00Z" }
];
