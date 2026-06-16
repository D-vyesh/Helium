-- ============================================================================
-- V14: Production Certification Hardening
-- Adds outbox deduplication, API key nonce enhancements, and operational views.
-- ============================================================================

-- 1. Outbox deduplication key for exactly-once publishing
alter table outbox_events
    add column if not exists deduplication_key varchar(255);

create unique index if not exists idx_outbox_events_dedup_key
    on outbox_events(deduplication_key)
    where deduplication_key is not null;

-- 2. Idempotency key on dead letters for replay deduplication
alter table outbox_dead_letters
    add column if not exists replayed_at timestamptz,
    add column if not exists replay_count integer not null default 0;

-- 3. Cleanup index for expired API key nonces
create index if not exists idx_api_key_nonces_cleanup
    on api_key_nonces(expires_at);

-- 4. Composite index for outbox operational queries
create index if not exists idx_outbox_events_status_created
    on outbox_events(status, created_at desc);

-- 5. Audit search acceleration indexes
create table if not exists security_audit_events (
    id uuid primary key,
    user_id uuid,
    event_type varchar(50) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_security_audit_event_type_created
    on security_audit_events(event_type, created_at desc);

create index if not exists idx_security_audit_user_type
    on security_audit_events(user_id, event_type, created_at desc);

-- 6. API key list query optimization
create index if not exists idx_api_keys_user_active
    on api_keys(user_id, created_at desc)
    where revoked_at is null;
