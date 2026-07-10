create table exchange_notifications (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    category varchar(32) not null,
    event_type varchar(80) not null,
    title varchar(160) not null,
    message varchar(1000) not null,
    payload jsonb not null default '{}'::jsonb,
    read_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz not null,
    constraint ck_exchange_notifications_category check (
        category in ('TRADING', 'WALLET', 'SECURITY', 'ACCOUNT', 'ADMIN', 'SYSTEM', 'MARKET')
    ),
    constraint ck_exchange_notifications_event_type check (event_type = upper(event_type))
);

create index ix_exchange_notifications_user_time
    on exchange_notifications(user_id, created_at desc)
    where deleted_at is null;

create index ix_exchange_notifications_unread
    on exchange_notifications(user_id, created_at desc)
    where read_at is null and deleted_at is null;
