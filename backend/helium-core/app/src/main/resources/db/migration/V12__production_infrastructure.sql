create table api_keys (
    key_id varchar(64) primary key,
    user_id uuid not null references auth_user_accounts(id),
    label varchar(120) not null,
    secret_hash varchar(128) not null,
    ip_allowlist text not null default '',
    created_at timestamptz not null,
    updated_at timestamptz not null default now(),
    revoked_at timestamptz,
    check (length(label) > 0),
    check (revoked_at is null or revoked_at >= created_at)
);

create index idx_api_keys_user_id on api_keys(user_id);
create unique index idx_api_keys_active_secret_hash on api_keys(secret_hash) where revoked_at is null;

create table api_key_audit_events (
    id uuid primary key,
    key_id varchar(64) not null,
    user_id uuid not null references auth_user_accounts(id),
    actor_id varchar(128) not null,
    action varchar(32) not null,
    created_at timestamptz not null,
    check (action in ('CREATED', 'REVOKED', 'ROTATED'))
);

create index idx_api_key_audit_user_created on api_key_audit_events(user_id, created_at desc);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(120) not null,
    event_type varchar(120) not null,
    payload jsonb not null,
    status varchar(24) not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz not null default now(),
    last_error text,
    published_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (status in ('PENDING', 'FAILED', 'PUBLISHED', 'DEAD_LETTER')),
    check (attempts >= 0),
    check (published_at is null or status = 'PUBLISHED')
);

create index idx_outbox_poll on outbox_events(status, next_attempt_at, created_at);
create index idx_outbox_aggregate on outbox_events(aggregate_type, aggregate_id);

create table outbox_dead_letters (
    id uuid primary key,
    outbox_event_id uuid not null unique references outbox_events(id),
    event_type varchar(120) not null,
    payload jsonb not null,
    error text not null,
    created_at timestamptz not null
);

create or replace function prevent_api_key_audit_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'api key audit records are immutable';
end;
$$;

create trigger trg_api_key_audit_no_update
before update or delete on api_key_audit_events
for each row execute function prevent_api_key_audit_mutation();

create or replace function prevent_outbox_dead_letter_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'outbox dead letters are immutable';
end;
$$;

create trigger trg_outbox_dead_letter_no_update
before update or delete on outbox_dead_letters
for each row execute function prevent_outbox_dead_letter_mutation();
