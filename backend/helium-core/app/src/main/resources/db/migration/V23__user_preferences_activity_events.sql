create table user_preferences (
    user_id uuid primary key references auth_user_accounts(id),
    theme varchar(16) not null,
    timezone varchar(80) not null,
    language varchar(16) not null,
    preferred_fiat varchar(12) not null,
    chart_interval varchar(16) not null,
    chart_style varchar(24) not null,
    default_market varchar(40) not null,
    sidebar_layout varchar(24) not null,
    workspace_layout jsonb not null default '{}'::jsonb,
    order_defaults jsonb not null default '{}'::jsonb,
    notification_preferences jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_user_preferences_theme check (theme in ('SYSTEM', 'DARK', 'LIGHT')),
    constraint ck_user_preferences_chart_style check (chart_style in ('CANDLES', 'BARS', 'LINE')),
    constraint ck_user_preferences_sidebar_layout check (sidebar_layout in ('EXPANDED', 'COMPACT', 'COLLAPSED')),
    constraint ck_user_preferences_market_upper check (default_market = upper(default_market)),
    constraint ck_user_preferences_fiat_upper check (preferred_fiat = upper(preferred_fiat))
);

create index ix_user_preferences_updated on user_preferences(updated_at desc);

create table exchange_activity_events (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    category varchar(32) not null,
    event_type varchar(80) not null,
    summary varchar(500) not null,
    status varchar(32) not null,
    actor_id varchar(120) not null,
    ip_address varchar(64),
    user_agent varchar(500),
    device_info varchar(500),
    country varchar(80),
    city varchar(80),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    constraint ck_exchange_activity_category check (
        category in ('AUTHENTICATION', 'TRADING', 'PORTFOLIO', 'SECURITY', 'SESSION', 'WATCHLIST', 'SETTINGS', 'API_KEY', 'WALLET', 'SYSTEM')
    ),
    constraint ck_exchange_activity_event_type check (event_type = upper(event_type)),
    constraint ck_exchange_activity_status check (status in ('SUCCESS', 'FAILED', 'PENDING', 'INFO'))
);

create index ix_exchange_activity_user_time on exchange_activity_events(user_id, created_at desc);
create index ix_exchange_activity_user_category_time on exchange_activity_events(user_id, category, created_at desc);

create function exchange_reject_activity_event_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'exchange activity events are immutable after insert';
end;
$$;

create trigger trg_exchange_activity_events_immutable
before update or delete on exchange_activity_events
for each row execute function exchange_reject_activity_event_mutation();
