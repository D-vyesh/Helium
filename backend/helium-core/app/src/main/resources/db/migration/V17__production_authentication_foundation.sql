alter table auth_credentials drop constraint ck_auth_credentials_argon2id;
alter table auth_credentials add constraint ck_auth_credentials_password_hash check (
    password_hash like '$2a$%'
    or password_hash like '$2b$%'
    or password_hash like '$2y$%'
    or password_hash like '$argon2id$%'
);

alter table auth_user_accounts drop constraint ck_auth_user_accounts_status;
update auth_user_accounts set status = 'EMAIL_UNVERIFIED' where status = 'PENDING_VERIFICATION';
alter table auth_user_accounts add constraint ck_auth_user_accounts_status check (
    status in ('EMAIL_UNVERIFIED', 'ACTIVE', 'LOCKED', 'SUSPENDED', 'CLOSED')
);

alter table auth_security_audit_events drop constraint ck_auth_security_audit_events_type;
alter table auth_security_audit_events add constraint ck_auth_security_audit_events_type check (
    event_type in (
        'AUTH.SIGNUP',
        'AUTH.EMAIL_VERIFIED',
        'AUTH.LOGIN_SUCCESS',
        'AUTH.LOGIN_FAILED',
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
);

create table login_attempts (
    id uuid primary key,
    user_id uuid references auth_user_accounts(id),
    email varchar(320) not null,
    success boolean not null,
    failure_reason varchar(120),
    ip_address varchar(64) not null,
    device_info varchar(255) not null,
    user_agent varchar(500) not null,
    created_at timestamptz not null,
    constraint ck_login_attempts_failure_reason check (
        (success = true and failure_reason is null)
        or (success = false and failure_reason is not null)
    )
);

create index ix_login_attempts_user_time on login_attempts(user_id, created_at desc);
create index ix_login_attempts_email_time on login_attempts(email, created_at desc);
create index ix_login_attempts_ip_time on login_attempts(ip_address, created_at desc);

create or replace view users as
select
    id,
    email,
    display_name,
    (email_verified_at is not null) as email_verified,
    status as account_status,
    failed_login_attempts,
    locked_until,
    created_at,
    updated_at
from auth_user_accounts;

create or replace view refresh_tokens as
select
    id,
    user_id,
    token_hash,
    status,
    ip_address,
    user_agent,
    created_at,
    last_seen_at,
    expires_at,
    revoked_at,
    revocation_reason
from auth_user_sessions;

create or replace view email_verification_tokens as
select
    id,
    user_id,
    token_hash,
    created_at,
    expires_at,
    consumed_at
from auth_email_verification_tokens;
