create table dashboard_watchlist_items (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    market_symbol varchar(80) not null references trading_markets(symbol),
    pinned boolean not null default false,
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_dashboard_watchlist_user_market unique (user_id, market_symbol),
    constraint ck_dashboard_watchlist_sort_order check (sort_order >= 0)
);

create index ix_dashboard_watchlist_user_sort
    on dashboard_watchlist_items(user_id, pinned desc, sort_order asc, created_at desc);
