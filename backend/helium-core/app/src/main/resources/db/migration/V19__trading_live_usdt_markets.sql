insert into trading_markets (
    symbol,
    base_asset,
    quote_asset,
    price_scale,
    quantity_scale,
    min_order_quantity,
    min_notional,
    enabled,
    created_at,
    updated_at
)
values
    ('BTC-USDT', 'BTC', 'USDT', 2, 6, 0.000010000000000000, 10.000000000000000000, true, now(), now()),
    ('ETH-USDT', 'ETH', 'USDT', 2, 5, 0.000100000000000000, 10.000000000000000000, true, now(), now()),
    ('SOL-USDT', 'SOL', 'USDT', 3, 3, 0.010000000000000000, 10.000000000000000000, true, now(), now())
on conflict (symbol) do update
set enabled = excluded.enabled,
    price_scale = excluded.price_scale,
    quantity_scale = excluded.quantity_scale,
    min_order_quantity = excluded.min_order_quantity,
    min_notional = excluded.min_notional,
    updated_at = now();

insert into trading_fee_schedules (
    id,
    market_symbol,
    maker_fee_rate,
    taker_fee_rate,
    sell_fee_asset,
    enabled,
    created_at,
    updated_at
)
select *
from (
    values
        ('10000000-0000-0000-0000-000000000001'::uuid, 'BTC-USDT', 0.0010000000, 0.0010000000, 'QUOTE', true, now(), now()),
        ('10000000-0000-0000-0000-000000000002'::uuid, 'ETH-USDT', 0.0010000000, 0.0010000000, 'QUOTE', true, now(), now()),
        ('10000000-0000-0000-0000-000000000003'::uuid, 'SOL-USDT', 0.0010000000, 0.0010000000, 'QUOTE', true, now(), now())
) as schedule(id, market_symbol, maker_fee_rate, taker_fee_rate, sell_fee_asset, enabled, created_at, updated_at)
where not exists (
    select 1
    from trading_fee_schedules existing
    where existing.market_symbol = schedule.market_symbol
      and existing.enabled = true
);
