-- Phase 2: Production Authentication
-- TOTP secrets (AES-256-GCM encrypted), backup codes, MFA sessions

create table auth_totp_secrets (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    encrypted_secret varchar(512) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_auth_totp_secrets_user unique (user_id)
);

create table auth_totp_backup_codes (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    code_hash char(64) not null,
    created_at timestamptz not null,
    used_at timestamptz,
    constraint ck_auth_totp_backup_codes_hash check (code_hash ~ '^[0-9a-f]{64}$')
);

create index ix_auth_totp_backup_codes_user on auth_totp_backup_codes(user_id);
create unique index uk_auth_totp_backup_codes_user_hash on auth_totp_backup_codes(user_id, code_hash);

create table auth_mfa_sessions (
    id uuid primary key,
    user_id uuid not null references auth_user_accounts(id),
    token_hash char(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    constraint uk_auth_mfa_sessions_token_hash unique (token_hash),
    constraint ck_auth_mfa_sessions_token_hash check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_auth_mfa_sessions_expiry check (expires_at > created_at)
);

create index ix_auth_mfa_sessions_expires_at on auth_mfa_sessions(expires_at)
    where consumed_at is null;

-- Extend audit event types for MFA
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
        'ROLE_REVOKED',
        'MFA_SETUP_INITIATED',
        'MFA_ENABLED',
        'MFA_DISABLED',
        'MFA_BACKUP_CODE_USED',
        'MFA_BACKUP_CODES_REGENERATED',
        'EMAIL_RESEND_REQUESTED'
    )
);
