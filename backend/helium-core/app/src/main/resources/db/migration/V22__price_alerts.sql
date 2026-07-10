create table price_alerts (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    market_symbol varchar(80) not null,
    condition_type varchar(40) not null,
    threshold decimal(38,18) not null,
    repeating boolean not null,
    enabled boolean not null,
    delivery_in_app boolean not null default true,
    delivery_email boolean not null default false,
    delivery_push boolean not null default false,
    expires_at timestamptz,
    last_evaluated_price decimal(38,18),
    triggered_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_price_alerts_condition check (
        condition_type in (
            'PRICE_ABOVE',
            'PRICE_BELOW',
            'CROSSES_ABOVE',
            'CROSSES_BELOW',
            'CHANGE_PERCENT_ABOVE',
            'VOLUME_ABOVE'
        )
    ),
    constraint ck_price_alerts_threshold_positive check (threshold > 0),
    constraint ck_price_alerts_market_upper check (market_symbol = upper(market_symbol)),
    constraint ck_price_alerts_delivery check (delivery_in_app or delivery_email or delivery_push)
);

create index ix_price_alerts_user_created on price_alerts(user_id, created_at desc);
create index ix_price_alerts_enabled on price_alerts(enabled, market_symbol)
    where enabled = true;
