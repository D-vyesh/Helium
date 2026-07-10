create table wallet_withdrawal_authorizations (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    user_id uuid not null references auth_user_accounts(id),
    email_token_hash char(64) not null,
    email_expires_at timestamptz not null,
    email_confirmed_at timestamptz,
    mfa_confirmed_at timestamptz,
    issued_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_wallet_withdrawal_authorizations_withdrawal unique (withdrawal_id),
    constraint ck_wallet_withdrawal_authorizations_token_hash check (email_token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_wallet_withdrawal_authorizations_expiry check (email_expires_at > issued_at)
);

create index ix_wallet_withdrawal_authorizations_user on wallet_withdrawal_authorizations(user_id);
create index ix_wallet_withdrawal_authorizations_expiry on wallet_withdrawal_authorizations(email_expires_at)
    where email_confirmed_at is null;

alter table wallet_audit_events
    drop constraint if exists ck_wallet_audit_events_type;

alter table wallet_audit_events
    add constraint ck_wallet_audit_events_type check (
        event_type in (
            'ASSET_REGISTERED',
            'NETWORK_REGISTERED',
            'DEPOSIT_ADDRESS_ASSIGNED',
            'DEPOSIT_DETECTED',
            'DEPOSIT_CONFIRMATIONS_UPDATED',
            'DEPOSIT_POSTED',
            'DEPOSIT_REORGED',
            'WITHDRAWAL_REQUESTED',
            'WITHDRAWAL_EMAIL_CONFIRMATION_ISSUED',
            'WITHDRAWAL_EMAIL_CONFIRMED',
            'WITHDRAWAL_MFA_CONFIRMED',
            'WITHDRAWAL_APPROVED',
            'WITHDRAWAL_REJECTED',
            'WITHDRAWAL_BROADCAST_RECORDED',
            'WITHDRAWAL_CONFIRMED',
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    );

alter table auth_security_audit_events
    drop constraint if exists ck_auth_security_audit_events_type;

alter table auth_security_audit_events
    add constraint ck_auth_security_audit_events_type check (
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
            'MFA_SENSITIVE_ACTION_VERIFIED',
            'EMAIL_RESEND_REQUESTED'
        )
    );
