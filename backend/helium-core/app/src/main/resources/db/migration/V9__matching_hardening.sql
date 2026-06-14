alter table matching_orders
    add column last_order_offset bigint;

update matching_orders
set last_order_offset = case
    when status in ('ACTIVE') then 1
    else greatest(received_sequence, 1)
end
where last_order_offset is null;

alter table matching_orders
    alter column last_order_offset set not null,
    add constraint uq_matching_orders_market_received_sequence unique (market_symbol, received_sequence),
    add constraint ck_matching_orders_order_offset_positive check (last_order_offset > 0),
    add constraint ck_matching_orders_status_remaining_strict check (
        (status = 'ACTIVE' and remaining_quantity = quantity)
        or (status = 'PARTIALLY_FILLED' and remaining_quantity > 0 and remaining_quantity < quantity)
        or (status = 'FILLED' and remaining_quantity = 0)
        or (status in ('CANCELLED', 'EXPIRED', 'REJECTED') and remaining_quantity >= 0 and remaining_quantity <= quantity)
    );

alter table matching_executions
    add column buyer_order_offset bigint,
    add column seller_order_offset bigint;

update matching_executions
set buyer_order_offset = sequence_number,
    seller_order_offset = sequence_number
where buyer_order_offset is null;

alter table matching_executions
    alter column buyer_order_offset set not null,
    alter column seller_order_offset set not null,
    add constraint ck_matching_executions_buyer_offset_positive check (buyer_order_offset > 1),
    add constraint ck_matching_executions_seller_offset_positive check (seller_order_offset > 1);

create or replace function matching_reject_order_immutable_field_mutation()
returns trigger
language plpgsql
as $$
begin
    if old.request_hash is distinct from new.request_hash
        or old.market_symbol is distinct from new.market_symbol
        or old.side is distinct from new.side
        or old.order_type is distinct from new.order_type
        or old.quantity is distinct from new.quantity
        or old.limit_price is distinct from new.limit_price
        or old.received_sequence is distinct from new.received_sequence then
        raise exception 'matching order immutable fields cannot be updated';
    end if;
    if new.last_order_offset < old.last_order_offset then
        raise exception 'matching order offset cannot decrease';
    end if;
    return new;
end;
$$;

create trigger trg_matching_orders_immutable_fields
before update on matching_orders
for each row execute function matching_reject_order_immutable_field_mutation();

create or replace function matching_reject_market_state_regression()
returns trigger
language plpgsql
as $$
declare
    sequence_value bigint;
begin
    if tg_op = 'UPDATE' and new.last_sequence < old.last_sequence then
        raise exception 'matching market state sequence cannot decrease';
    end if;
    select current_sequence into sequence_value
    from matching_sequences
    where market_symbol = new.market_symbol;
    if sequence_value is not null and new.last_sequence > sequence_value then
        raise exception 'matching market state cannot exceed matching sequence';
    end if;
    return new;
end;
$$;

create trigger trg_matching_market_state_monotonic
before insert or update on matching_market_state
for each row execute function matching_reject_market_state_regression();
