import { AdminOverview } from "@/features/admin/components/admin-overview";
import { LoginForm } from "@/features/auth/components/auth-forms";
import { MarketList } from "@/features/market-data/components/market-data-panels";
import { SettingsPanel } from "@/features/settings/components/settings-panels";
import { TradingTerminal } from "@/features/trading/components/trading-terminal";
import { AssetList } from "@/features/wallet/components/wallet-panels";

const componentContracts = {
  AdminOverview,
  AssetList,
  LoginForm,
  MarketList,
  SettingsPanel,
  TradingTerminal
} satisfies Record<string, () => React.ReactNode>;

void componentContracts;
