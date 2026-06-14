create table matching_orders (
    order_id uuid not null primary key,
    request_hash char(64) not null,
    market_symbol varchar(80) not null,
    side varchar(20) not null,
    order_type varchar(20) not null,
    quantity numeric(38, 18) not null,
    remaining_quantity numeric(38, 18) not null,
    limit_price numeric(38, 18) not null,
    status varchar(40) not null,
    received_sequence bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    constraint ck_matching_orders_request_hash check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_matching_orders_side check (side in ('BUY', 'SELL')),
    constraint ck_matching_orders_type check (order_type in ('LIMIT')),
    constraint ck_matching_orders_quantity_positive check (quantity > 0),
    constraint ck_matching_orders_remaining_non_negative check (remaining_quantity >= 0),
    constraint ck_matching_orders_remaining_not_greater check (remaining_quantity <= quantity),
    constraint ck_matching_orders_price_positive check (limit_price > 0),
    constraint ck_matching_orders_status check (status in ('ACTIVE', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'EXPIRED', 'REJECTED')),
    constraint ck_matching_orders_sequence_positive check (received_sequence > 0),
    constraint ck_matching_orders_status_remaining check (
        (status = 'FILLED' and remaining_quantity = 0)
        or (status in ('ACTIVE', 'PARTIALLY_FILLED', 'CANCELLED', 'EXPIRED', 'REJECTED') and remaining_quantity >= 0)
    )
);

create index ix_matching_orders_book_bid
    on matching_orders (market_symbol, side, limit_price desc, received_sequence)
    where status in ('ACTIVE', 'PARTIALLY_FILLED') and side = 'BUY';

create index ix_matching_orders_book_ask
    on matching_orders (market_symbol, side, limit_price asc, received_sequence)
    where status in ('ACTIVE', 'PARTIALLY_FILLED') and side = 'SELL';

create table matching_executions (
    id uuid not null primary key,
    execution_id varchar(160) not null,
    match_id varchar(160) not null,
    market_symbol varchar(80) not null,
    buyer_order_id uuid not null,
    seller_order_id uuid not null,
    maker_order_id uuid not null,
    taker_order_id uuid not null,
    quantity numeric(38, 18) not null,
    price numeric(38, 18) not null,
    sequence_number bigint not null,
    created_at timestamp with time zone not null,
    constraint uq_matching_executions_execution unique (execution_id),
    constraint uq_matching_executions_market_sequence unique (market_symbol, sequence_number),
    constraint fk_matching_executions_buyer foreign key (buyer_order_id) references matching_orders(order_id),
    constraint fk_matching_executions_seller foreign key (seller_order_id) references matching_orders(order_id),
    constraint fk_matching_executions_maker foreign key (maker_order_id) references matching_orders(order_id),
    constraint fk_matching_executions_taker foreign key (taker_order_id) references matching_orders(order_id),
    constraint ck_matching_executions_distinct_counterparties check (buyer_order_id <> seller_order_id),
    constraint ck_matching_executions_quantity_positive check (quantity > 0),
    constraint ck_matching_executions_price_positive check (price > 0),
    constraint ck_matching_executions_sequence_positive check (sequence_number > 0)
);

create table matching_sequences (
    market_symbol varchar(80) not null primary key,
    current_sequence bigint not null,
    version bigint not null,
    constraint ck_matching_sequences_non_negative check (current_sequence >= 0)
);

create table matching_market_state (
    market_symbol varchar(80) not null primary key,
    status varchar(40) not null,
    last_sequence bigint not null,
    updated_at timestamp with time zone not null,
    constraint ck_matching_market_state_status check (status in ('ACTIVE', 'HALTED')),
    constraint ck_matching_market_state_sequence_non_negative check (last_sequence >= 0)
);

create or replace function matching_reject_execution_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'matching executions are append-only after insert';
end;
$$;

create trigger trg_matching_executions_append_only
before update or delete on matching_executions
for each row execute function matching_reject_execution_mutation();

create or replace function matching_reject_sequence_regression()
returns trigger
language plpgsql
as $$
begin
    if new.current_sequence < old.current_sequence then
        raise exception 'matching sequence cannot decrease';
    end if;
    return new;
end;
$$;

create trigger trg_matching_sequences_monotonic
before update on matching_sequences
for each row execute function matching_reject_sequence_regression();
