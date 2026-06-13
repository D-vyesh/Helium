create table auth_user_accounts (
    id uuid primary key,
    email varchar(320) not null,
    display_name varchar(120) not null,
    status varchar(40) not null,
    failed_login_attempts integer not null default 0,
    locked_until timestamptz,
    email_verified_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_auth_user_accounts_email unique (email),
    constraint ck_auth_user_accounts_email_normalized check (email = lower(email)),
    constraint ck_auth_user_accounts_failed_attempts check (failed_login_attempts >= 0),
    constraint ck_auth_user_accounts_status check (
        status in ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'SUSPENDED', 'CLOSED')
    ),
    constraint ck_auth_user_accounts_lock_state check (
        (status = 'LOCKED' and locked_until is not null)
        or (status <> 'LOCKED' and locked_until is null)
    ),
    constraint ck_auth_user_accounts_verification_state check (
        status <> 'ACTIVE' or email_verified_at is not null
    )
);

create table auth_credentials (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    password_hash varchar(255) not null,
    password_changed_at timestamptz not null,
    must_change_password boolean not null default false,
    version bigint not null default 0,
    constraint uk_auth_credentials_user unique (user_id),
    constraint ck_auth_credentials_argon2id check (password_hash like '$argon2id$%')
);

create table auth_user_sessions (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    token_hash char(64) not null,
    status varchar(40) not null,
    ip_address varchar(64) not null,
    user_agent varchar(500) not null,
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    revocation_reason varchar(160),
    version bigint not null default 0,
    constraint uk_auth_user_sessions_token_hash unique (token_hash),
    constraint ck_auth_user_sessions_token_hash check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_auth_user_sessions_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED')),
    constraint ck_auth_user_sessions_expiry check (expires_at > created_at),
    constraint ck_auth_user_sessions_revocation check (
        (status = 'REVOKED' and revoked_at is not null and revocation_reason is not null)
        or (status <> 'REVOKED' and revoked_at is null and revocation_reason is null)
    )
);

create table auth_role_grants (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    role varchar(40) not null,
    granted_by uuid not null references auth_user_accounts(id),
    granted_at timestamptz not null,
    revoked_by uuid references auth_user_accounts(id),
    revoked_at timestamptz,
    constraint ck_auth_role_grants_role check (role in ('USER', 'ADMIN', 'SUPPORT', 'COMPLIANCE', 'AUDITOR')),
    constraint ck_auth_role_grants_revocation check (
        (revoked_at is null and revoked_by is null)
        or (revoked_at is not null and revoked_by is not null)
    )
);

create unique index uk_auth_role_grants_active
    on auth_role_grants(user_id, role)
    where revoked_at is null;

create table auth_email_verification_tokens (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    token_hash char(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    constraint uk_auth_email_verification_tokens_hash unique (token_hash),
    constraint ck_auth_email_verification_tokens_hash check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_auth_email_verification_tokens_expiry check (expires_at > created_at)
);

create table auth_password_reset_tokens (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    token_hash char(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    constraint uk_auth_password_reset_tokens_hash unique (token_hash),
    constraint ck_auth_password_reset_tokens_hash check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_auth_password_reset_tokens_expiry check (expires_at > created_at)
);

create table auth_mfa_methods (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    type varchar(40) not null,
    status varchar(40) not null,
    created_at timestamptz not null,
    enabled_at timestamptz,
    disabled_at timestamptz,
    constraint ck_auth_mfa_methods_type check (type in ('TOTP')),
    constraint ck_auth_mfa_methods_status check (status in ('PENDING', 'ENABLED', 'DISABLED')),
    constraint ck_auth_mfa_methods_lifecycle check (
        (status = 'PENDING' and enabled_at is null and disabled_at is null)
        or (status = 'ENABLED' and enabled_at is not null and disabled_at is null)
        or (status = 'DISABLED' and disabled_at is not null)
    )
);

create unique index uk_auth_mfa_methods_user_type on auth_mfa_methods(user_id, type);

create table auth_login_attempt_throttles (
    id uuid primary key,
    subject_hash char(64) not null,
    source_hash char(64) not null,
    failed_attempts integer not null,
    window_started_at timestamptz not null,
    blocked_until timestamptz,
    version bigint not null default 0,
    constraint uk_auth_login_attempt_throttles_subject_source unique (subject_hash, source_hash),
    constraint ck_auth_login_attempt_throttles_subject_hash check (subject_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_auth_login_attempt_throttles_source_hash check (source_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_auth_login_attempt_throttles_failed_attempts check (failed_attempts >= 0)
);

create table auth_security_audit_events (
    id uuid primary key,
    event_type varchar(60) not null,
    user_id uuid references auth_user_accounts(id),
    session_id uuid references auth_user_sessions(id),
    actor_id varchar(120) not null,
    ip_address varchar(64) not null,
    user_agent varchar(500) not null,
    details varchar(1000) not null,
    occurred_at timestamptz not null,
    constraint ck_auth_security_audit_events_type check (
        event_type in (
            'USER_REGISTERED',
            'EMAIL_VERIFICATION_ISSUED',
            'EMAIL_VERIFIED',
            'LOGIN_SUCCEEDED',
            'LOGIN_FAILED',
            'ACCOUNT_LOCKED',
            'ACCOUNT_UNLOCKED',
            'ACCOUNT_SUSPENDED',
            'ACCOUNT_REACTIVATED',
            'LOGOUT',
            'SESSION_REVOKED',
            'PASSWORD_RESET_REQUESTED',
            'PASSWORD_RESET_COMPLETED',
            'PASSWORD_CHANGED',
            'ROLE_GRANTED',
            'ROLE_REVOKED'
        )
    )
);

create index ix_auth_user_sessions_user_status on auth_user_sessions(user_id, status);
create index ix_auth_user_sessions_expires_at on auth_user_sessions(expires_at) where status = 'ACTIVE';
create index ix_auth_email_verification_tokens_user on auth_email_verification_tokens(user_id);
create index ix_auth_password_reset_tokens_user on auth_password_reset_tokens(user_id);
create index ix_auth_login_attempt_throttles_blocked_until on auth_login_attempt_throttles(blocked_until)
    where blocked_until is not null;
create index ix_auth_security_audit_events_user_time on auth_security_audit_events(user_id, occurred_at desc);
create index ix_auth_security_audit_events_time on auth_security_audit_events(occurred_at desc);

create function auth_reject_audit_event_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'auth security audit events are immutable after insert';
end;
$$;

create trigger trg_auth_security_audit_events_immutable
before update or delete on auth_security_audit_events
for each row execute function auth_reject_audit_event_mutation();
