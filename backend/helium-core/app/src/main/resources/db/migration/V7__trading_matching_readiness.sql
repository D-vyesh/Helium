alter table trading_orders
    add column fee_rate numeric(18, 10),
    add column fee_asset_code varchar(32),
    add column fee_policy_version varchar(120),
    add column last_matching_offset bigint;

update trading_orders orders
set fee_rate = 0,
    fee_asset_code = markets.quote_asset,
    fee_policy_version = 'legacy:v7',
    last_matching_offset = case
        when orders.status in ('OPEN', 'PARTIALLY_FILLED', 'CANCEL_REQUESTED', 'CANCELLED', 'EXPIRED', 'FILLED') then 1
        else 0
    end
from trading_markets markets
where orders.market_symbol = markets.symbol
  and orders.fee_rate is null;

alter table trading_orders
    alter column fee_rate set not null,
    alter column fee_asset_code set not null,
    alter column fee_policy_version set not null,
    alter column last_matching_offset set not null,
    add constraint ck_trading_orders_fee_rate check (fee_rate >= 0 and fee_rate <= 1),
    add constraint ck_trading_orders_fee_asset_code check (fee_asset_code ~ '^[A-Z0-9]{2,32}$'),
    add constraint ck_trading_orders_last_matching_offset check (last_matching_offset >= 0),
    add constraint ck_trading_orders_filled_terminal_consistency check (
        status <> 'FILLED' or filled_quantity = quantity
    );

alter table trading_settlement_instructions
    disable trigger trg_trading_settlement_instructions_append_only;

alter table trading_settlement_instructions
    drop constraint if exists ck_trading_settlement_sequence_positive;

update trading_settlement_instructions
set execution_sequence = 1
where execution_sequence is not null
  and execution_sequence < 1;

alter table trading_settlement_instructions
    add column buyer_order_id uuid,
    add column seller_order_id uuid,
    add column buyer_fee_amount numeric(38, 18),
    add column buyer_fee_asset_code varchar(32),
    add column seller_fee_amount numeric(38, 18),
    add column seller_fee_asset_code varchar(32),
    add column buyer_reserve_consumed_amount numeric(38, 18),
    add column seller_reserve_consumed_amount numeric(38, 18);

update trading_settlement_instructions
set buyer_order_id = order_id,
    seller_order_id = order_id,
    buyer_fee_amount = fee_amount,
    buyer_fee_asset_code = fee_asset_code,
    seller_fee_amount = 0,
    seller_fee_asset_code = fee_asset_code,
    buyer_reserve_consumed_amount = reserve_consumed_amount,
    seller_reserve_consumed_amount = 0
where buyer_order_id is null;

alter table trading_settlement_instructions
    alter column buyer_order_id set not null,
    alter column seller_order_id set not null,
    alter column buyer_fee_amount set not null,
    alter column buyer_fee_asset_code set not null,
    alter column seller_fee_amount set not null,
    alter column seller_fee_asset_code set not null,
    alter column buyer_reserve_consumed_amount set not null,
    alter column seller_reserve_consumed_amount set not null,
    add constraint ck_trading_settlement_sequence_positive check (execution_sequence > 0),
    add constraint ck_trading_settlement_buyer_seller_distinct check (buyer_order_id <> seller_order_id) not valid,
    add constraint fk_trading_settlement_buyer_order foreign key (buyer_order_id) references trading_orders(id) not valid,
    add constraint fk_trading_settlement_seller_order foreign key (seller_order_id) references trading_orders(id) not valid,
    add constraint ck_trading_settlement_buyer_fee_non_negative check (buyer_fee_amount >= 0),
    add constraint ck_trading_settlement_seller_fee_non_negative check (seller_fee_amount >= 0),
    add constraint ck_trading_settlement_buyer_reserve_non_negative check (buyer_reserve_consumed_amount >= 0),
    add constraint ck_trading_settlement_seller_reserve_non_negative check (seller_reserve_consumed_amount >= 0),
    add constraint ck_trading_settlement_buyer_fee_asset check (buyer_fee_asset_code ~ '^[A-Z0-9]{2,32}$'),
    add constraint ck_trading_settlement_seller_fee_asset check (seller_fee_asset_code ~ '^[A-Z0-9]{2,32}$');

create unique index uq_trading_settlement_buyer_sequence
    on trading_settlement_instructions (buyer_order_id, execution_sequence);

create unique index uq_trading_settlement_seller_sequence
    on trading_settlement_instructions (seller_order_id, execution_sequence);

alter table trading_settlement_instructions
    enable trigger trg_trading_settlement_instructions_append_only;

create or replace function trading_reject_terminal_order_mutation()
returns trigger
language plpgsql
as $$
begin
    if old.status in ('FILLED', 'CANCELLED', 'EXPIRED', 'REJECTED') and old.status is distinct from new.status then
        raise exception 'terminal trading orders cannot change status';
    end if;
    if old.status = 'CANCELLED' and old.filled_quantity is distinct from new.filled_quantity then
        raise exception 'cancelled trading orders cannot receive fills';
    end if;
    return new;
end;
$$;

create trigger trg_trading_orders_terminal_guard
before update on trading_orders
for each row execute function trading_reject_terminal_order_mutation();

create or replace function trading_require_order_reservation()
returns trigger
language plpgsql
as $$
begin
    if new.status in ('OPEN', 'PARTIALLY_FILLED', 'CANCEL_REQUESTED', 'FILLED', 'CANCELLED', 'EXPIRED') then
        if not exists (
            select 1
            from trading_order_reservations reservation
            where reservation.order_id = new.id
        ) then
            raise exception 'trading order % requires a reservation before status %', new.id, new.status;
        end if;
    end if;
    return null;
end;
$$;

create constraint trigger trg_trading_orders_require_reservation
after insert or update of status on trading_orders
deferrable initially deferred
for each row execute function trading_require_order_reservation();
