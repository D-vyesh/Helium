create table market_data_sequences (
    market_symbol varchar(41) not null primary key,
    last_sequence bigint not null,
    updated_at timestamp with time zone not null,
    constraint ck_market_data_sequences_non_negative check (last_sequence >= 0)
);

create table market_data_public_trades (
    execution_id varchar(120) not null primary key,
    market_symbol varchar(41) not null,
    match_id varchar(120) not null,
    buyer_order_id uuid not null,
    seller_order_id uuid not null,
    maker_order_id uuid not null,
    taker_order_id uuid not null,
    price numeric(38, 18) not null,
    quantity numeric(38, 18) not null,
    market_sequence bigint not null,
    trade_hash char(64) not null,
    traded_at timestamp with time zone not null,
    constraint uq_market_data_public_trades_market_sequence unique (market_symbol, market_sequence),
    constraint ck_market_data_public_trades_price_positive check (price > 0),
    constraint ck_market_data_public_trades_quantity_positive check (quantity > 0),
    constraint ck_market_data_public_trades_sequence_positive check (market_sequence > 0),
    constraint ck_market_data_public_trades_hash check (trade_hash ~ '^[0-9a-f]{64}$')
);

create index ix_market_data_public_trades_market_time
    on market_data_public_trades (market_symbol, traded_at, market_sequence);

create table market_data_tickers (
    market_symbol varchar(41) not null primary key,
    last_price numeric(38, 18) not null,
    open_price_24h numeric(38, 18) not null,
    high_price_24h numeric(38, 18) not null,
    low_price_24h numeric(38, 18) not null,
    volume_24h numeric(38, 18) not null,
    quote_volume_24h numeric(38, 18) not null,
    trade_count_24h bigint not null,
    enabled boolean not null,
    updated_at timestamp with time zone not null,
    constraint ck_market_data_tickers_prices_non_negative check (
        last_price >= 0 and open_price_24h >= 0 and high_price_24h >= 0 and low_price_24h >= 0
    ),
    constraint ck_market_data_tickers_volumes_non_negative check (volume_24h >= 0 and quote_volume_24h >= 0),
    constraint ck_market_data_tickers_count_non_negative check (trade_count_24h >= 0)
);

create table market_data_candles (
    id uuid not null primary key,
    market_symbol varchar(41) not null,
    interval_name varchar(16) not null,
    open_time timestamp with time zone not null,
    close_time timestamp with time zone not null,
    open_price numeric(38, 18) not null,
    high_price numeric(38, 18) not null,
    low_price numeric(38, 18) not null,
    close_price numeric(38, 18) not null,
    volume numeric(38, 18) not null,
    quote_volume numeric(38, 18) not null,
    trade_count bigint not null,
    closed boolean not null,
    constraint uq_market_data_candles_bucket unique (market_symbol, interval_name, open_time),
    constraint ck_market_data_candles_prices_positive check (
        open_price > 0 and high_price > 0 and low_price > 0 and close_price > 0
    ),
    constraint ck_market_data_candles_price_bounds check (high_price >= low_price),
    constraint ck_market_data_candles_volume_positive check (volume > 0 and quote_volume > 0),
    constraint ck_market_data_candles_count_positive check (trade_count > 0),
    constraint ck_market_data_candles_time check (close_time > open_time)
);

create index ix_market_data_candles_market_interval_time
    on market_data_candles (market_symbol, interval_name, open_time);

create table market_data_order_book_snapshots (
    id uuid not null primary key,
    market_symbol varchar(41) not null,
    market_sequence bigint not null,
    bids_json text not null,
    asks_json text not null,
    snapshot_hash char(64) not null,
    created_at timestamp with time zone not null,
    constraint uq_market_data_order_book_snapshots_sequence unique (market_symbol, market_sequence),
    constraint ck_market_data_order_book_snapshots_sequence_positive check (market_sequence > 0),
    constraint ck_market_data_order_book_snapshots_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);

create table market_data_order_book_deltas (
    id uuid not null primary key,
    market_symbol varchar(41) not null,
    market_sequence bigint not null,
    side varchar(4) not null,
    price numeric(38, 18) not null,
    quantity numeric(38, 18) not null,
    action varchar(16) not null,
    delta_hash char(64) not null,
    created_at timestamp with time zone not null,
    constraint uq_market_data_order_book_deltas_level unique (market_symbol, market_sequence, side, price),
    constraint ck_market_data_order_book_deltas_side check (side in ('BID', 'ASK')),
    constraint ck_market_data_order_book_deltas_action check (action in ('UPSERT', 'DELETE')),
    constraint ck_market_data_order_book_deltas_sequence_positive check (market_sequence > 0),
    constraint ck_market_data_order_book_deltas_price_positive check (price > 0),
    constraint ck_market_data_order_book_deltas_quantity_non_negative check (quantity >= 0),
    constraint ck_market_data_order_book_deltas_hash check (delta_hash ~ '^[0-9a-f]{64}$')
);

create or replace function market_data_reject_immutable_projection_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'market data projection table % is immutable after insert', tg_table_name;
end;
$$;

create trigger trg_market_data_public_trades_immutable
before update on market_data_public_trades
for each row execute function market_data_reject_immutable_projection_mutation();

create trigger trg_market_data_order_book_snapshots_immutable
before update on market_data_order_book_snapshots
for each row execute function market_data_reject_immutable_projection_mutation();

create trigger trg_market_data_order_book_deltas_immutable
before update on market_data_order_book_deltas
for each row execute function market_data_reject_immutable_projection_mutation();

create or replace function market_data_reject_sequence_regression()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'UPDATE' and new.last_sequence < old.last_sequence then
        raise exception 'market data sequence cannot regress';
    end if;
    return new;
end;
$$;

create trigger trg_market_data_sequences_monotonic
before update on market_data_sequences
for each row execute function market_data_reject_sequence_regression();
