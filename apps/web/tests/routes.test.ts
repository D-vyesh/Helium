const productRoutes = [
  "/dashboard",
  "/wallet",
  "/wallet/deposit",
  "/wallet/withdraw",
  "/markets",
  "/trade",
  "/orders/open",
  "/orders/history",
  "/trades/history",
  "/settings",
  "/admin",
  "/login",
  "/register",
  "/password-reset",
  "/email-verification"
] as const;

type RequiredRoute =
  | "/dashboard"
  | "/wallet"
  | "/wallet/deposit"
  | "/wallet/withdraw"
  | "/markets"
  | "/trade"
  | "/orders/open"
  | "/orders/history"
  | "/trades/history"
  | "/settings"
  | "/admin"
  | "/login"
  | "/register"
  | "/password-reset"
  | "/email-verification";

const routeCoverage: readonly RequiredRoute[] = productRoutes;

void routeCoverage;
