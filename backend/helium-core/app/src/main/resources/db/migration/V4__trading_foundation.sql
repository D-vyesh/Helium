CREATE TABLE trading_markets (
    symbol VARCHAR(80) NOT NULL PRIMARY KEY,
    base_asset VARCHAR(32) NOT NULL,
    quote_asset VARCHAR(32) NOT NULL,
    price_scale INT NOT NULL,
    quantity_scale INT NOT NULL,
    min_order_quantity DECIMAL(38,18) NOT NULL,
    min_notional DECIMAL(38,18) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE trading_orders (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    client_order_id VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    market_symbol VARCHAR(80) NOT NULL,
    side VARCHAR(20) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    time_in_force VARCHAR(20) NOT NULL,
    quantity DECIMAL(38,18) NOT NULL,
    limit_price DECIMAL(38,18),
    filled_quantity DECIMAL(38,18) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_trading_orders_user_client UNIQUE (user_id, client_order_id)
);

CREATE TABLE trading_order_history (
    id UUID NOT NULL PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    actor_id VARCHAR(120) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE trading_order_reservations (
    id UUID NOT NULL PRIMARY KEY,
    order_id UUID NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    reserved_amount DECIMAL(38,18) NOT NULL,
    estimated_fee DECIMAL(38,18) NOT NULL,
    reserve_ledger_transaction_id UUID NOT NULL,
    release_ledger_transaction_id UUID,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE trading_fee_schedules (
    id UUID NOT NULL PRIMARY KEY,
    market_symbol VARCHAR(80) NOT NULL,
    maker_fee_rate DECIMAL(18,10) NOT NULL,
    taker_fee_rate DECIMAL(18,10) NOT NULL,
    sell_fee_asset VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE trading_settlement_instructions (
    id UUID NOT NULL PRIMARY KEY,
    execution_id VARCHAR(120) NOT NULL,
    order_id UUID NOT NULL,
    market_symbol VARCHAR(80) NOT NULL,
    quantity DECIMAL(38,18) NOT NULL,
    price DECIMAL(38,18) NOT NULL,
    fee_amount DECIMAL(38,18) NOT NULL,
    status VARCHAR(40) NOT NULL,
    ledger_transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE
);
