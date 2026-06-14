alter table trading_settlement_instructions
    add column execution_hash char(64),
    add column execution_sequence bigint,
    add column fee_asset_code varchar(32),
    add column reserve_consumed_amount numeric(38, 18);

update trading_settlement_instructions
set execution_hash = repeat('0', 64),
    execution_sequence = 0,
    fee_asset_code = split_part(market_symbol, '-', 2),
    reserve_consumed_amount = 0
where execution_hash is null;

alter table trading_settlement_instructions
    alter column execution_hash set not null,
    alter column execution_sequence set not null,
    alter column fee_asset_code set not null,
    alter column reserve_consumed_amount set not null;

alter table trading_orders
    add constraint ck_trading_orders_quantity_strict_positive check (quantity > 0),
    add constraint ck_trading_orders_filled_not_greater_than_quantity check (filled_quantity <= quantity),
    add constraint ck_trading_orders_status check (
        status in (
            'RECEIVED',
            'VALIDATED',
            'FUNDS_RESERVED',
            'SENT_TO_MATCHING',
            'OPEN',
            'PARTIALLY_FILLED',
            'FILLED',
            'CANCEL_REQUESTED',
            'CANCELLED',
            'EXPIRED',
            'REJECTED'
        )
    ),
    add constraint ck_trading_orders_side check (side in ('BUY', 'SELL')),
    add constraint ck_trading_orders_type check (order_type in ('MARKET', 'LIMIT')),
    add constraint ck_trading_orders_tif check (time_in_force in ('GTC', 'IOC', 'FOK', 'DAY')),
    add constraint ck_trading_orders_request_hash check (request_hash ~ '^[0-9a-f]{64}$'),
    add constraint fk_trading_orders_market foreign key (market_symbol) references trading_markets(symbol);

alter table trading_markets
    add constraint ck_trading_markets_price_scale check (price_scale between 0 and 18),
    add constraint ck_trading_markets_quantity_scale check (quantity_scale between 0 and 18),
    add constraint ck_trading_markets_min_quantity check (min_order_quantity >= 0),
    add constraint ck_trading_markets_min_notional check (min_notional >= 0),
    add constraint ck_trading_markets_assets_differ check (base_asset <> quote_asset);

alter table trading_order_reservations
    add constraint uq_trading_order_reservations_order unique (order_id),
    add constraint fk_trading_order_reservations_order foreign key (order_id) references trading_orders(id),
    add constraint fk_trading_order_reservations_reserve_ledger foreign key (reserve_ledger_transaction_id) references ledger_transactions(id),
    add constraint fk_trading_order_reservations_release_ledger foreign key (release_ledger_transaction_id) references ledger_transactions(id),
    add constraint ck_trading_order_reservations_reserved_positive check (reserved_amount > 0),
    add constraint ck_trading_order_reservations_fee_non_negative check (estimated_fee >= 0),
    add constraint ck_trading_order_reservations_status check (status in ('ACTIVE', 'RELEASED')),
    add constraint ck_trading_order_reservations_release_state check (
        (status = 'ACTIVE' and released_at is null and release_ledger_transaction_id is null)
        or (status = 'RELEASED' and released_at is not null)
    );

alter table trading_fee_schedules
    add constraint uq_trading_fee_schedules_market unique (market_symbol),
    add constraint fk_trading_fee_schedules_market foreign key (market_symbol) references trading_markets(symbol),
    add constraint ck_trading_fee_schedules_rates check (
        maker_fee_rate >= 0 and maker_fee_rate <= 1 and taker_fee_rate >= 0 and taker_fee_rate <= 1
    ),
    add constraint ck_trading_fee_schedules_sell_fee_asset check (sell_fee_asset in ('BASE', 'QUOTE'));

alter table trading_order_history
    add constraint fk_trading_order_history_order foreign key (order_id) references trading_orders(id),
    add constraint ck_trading_order_history_status check (
        status in (
            'RECEIVED',
            'VALIDATED',
            'FUNDS_RESERVED',
            'SENT_TO_MATCHING',
            'OPEN',
            'PARTIALLY_FILLED',
            'FILLED',
            'CANCEL_REQUESTED',
            'CANCELLED',
            'EXPIRED',
            'REJECTED'
        )
    );

alter table trading_settlement_instructions
    add constraint uq_trading_settlement_instructions_order_sequence unique (order_id, execution_sequence),
    add constraint fk_trading_settlement_instructions_order foreign key (order_id) references trading_orders(id),
    add constraint fk_trading_settlement_instructions_ledger foreign key (ledger_transaction_id) references ledger_transactions(id),
    add constraint ck_trading_settlement_execution_hash check (execution_hash ~ '^[0-9a-f]{64}$'),
    add constraint ck_trading_settlement_sequence_positive check (execution_sequence > 0),
    add constraint ck_trading_settlement_qty_strict_positive check (quantity > 0),
    add constraint ck_trading_settlement_price_strict_positive check (price > 0),
    add constraint ck_trading_settlement_fee_non_negative check (fee_amount >= 0),
    add constraint ck_trading_settlement_reserve_consumed_non_negative check (reserve_consumed_amount >= 0),
    add constraint ck_trading_settlement_status check (status in ('PENDING', 'SETTLED', 'FAILED')),
    add constraint ck_trading_settlement_settled_state check (
        (status = 'SETTLED' and ledger_transaction_id is not null and settled_at is not null)
        or (status = 'PENDING' and ledger_transaction_id is null and settled_at is null)
        or (status = 'FAILED' and settled_at is null)
    );

create or replace function trading_reject_settlement_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'trading settlement instructions are append-only after insert';
end;
$$;

create trigger trg_trading_settlement_instructions_append_only
before update or delete on trading_settlement_instructions
for each row execute function trading_reject_settlement_mutation();
