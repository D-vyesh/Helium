-- V33: Add stop_price and time_in_force columns to support MARKET, STOP_LIMIT, and POST_ONLY order types.
-- matching_orders gains stop_price (nullable) and time_in_force (required, defaulting to 'GTC' for
-- existing rows so the migration is non-destructive).
-- trading_orders gains stop_price (nullable) to mirror the matching layer state.

ALTER TABLE matching_orders
    ADD COLUMN IF NOT EXISTS stop_price   NUMERIC(38, 18),
    ADD COLUMN IF NOT EXISTS time_in_force VARCHAR(20) NOT NULL DEFAULT 'GTC';

-- Sparse index: only rows in STOP_PENDING status will have stop_price set, so a partial index
-- keeps the trigger query fast even with millions of resting orders.
CREATE INDEX IF NOT EXISTS idx_matching_orders_stop_pending
    ON matching_orders (market_symbol, side, stop_price)
    WHERE status = 'STOP_PENDING';

ALTER TABLE trading_orders
    ADD COLUMN IF NOT EXISTS stop_price NUMERIC(38, 18);
