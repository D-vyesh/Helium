alter table wallet_withdrawal_queue
    drop constraint if exists ck_wallet_withdrawal_queue_status;

alter table wallet_withdrawal_queue
    add constraint ck_wallet_withdrawal_queue_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'SIGN_FAILED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_withdrawal_queue_transitions
    drop constraint if exists ck_wallet_withdrawal_queue_transitions_status;

alter table wallet_withdrawal_queue_transitions
    add constraint ck_wallet_withdrawal_queue_transitions_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'SIGN_FAILED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_withdrawal_queue
    add column sign_attempts integer not null default 0,
    add column next_sign_attempt_at timestamptz;

create table wallet_custody_keys (
    id uuid primary key,
    asset_code varchar(32) not null references wallet_assets(code),
    key_alias varchar(160) not null,
    key_version varchar(80) not null,
    provider varchar(80) not null,
    algorithm varchar(40) not null,
    public_key_hex text,
    status varchar(30) not null,
    created_at timestamptz not null,
    activated_at timestamptz,
    retired_at timestamptz,
    version bigint not null default 0,
    constraint uk_wallet_custody_keys_alias_version unique (key_alias, key_version),
    constraint ck_wallet_custody_keys_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_custody_keys_status check (status in ('ACTIVE', 'VERIFY_ONLY', 'ROTATING', 'RETIRED')),
    constraint ck_wallet_custody_keys_algorithm check (algorithm in ('BTC_PSBT', 'SECP256K1_ECDSA', 'ED25519'))
);

create unique index uk_wallet_custody_keys_one_active
    on wallet_custody_keys(asset_code)
    where status = 'ACTIVE';

create table wallet_signed_transactions (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    unsigned_transaction_id uuid not null references wallet_unsigned_transactions(id),
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    format varchar(40) not null,
    serialized_payload text not null,
    signing_digest varchar(160) not null,
    signature text not null,
    custody_provider varchar(80) not null,
    key_alias varchar(160) not null,
    key_version varchar(80) not null,
    algorithm varchar(40) not null,
    signed_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_wallet_signed_transactions_withdrawal unique (withdrawal_id),
    constraint ck_wallet_signed_transactions_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_signed_transactions_network_upper check (network_code = upper(network_code))
);

create table wallet_custody_signing_audit (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    asset_code varchar(32) not null references wallet_assets(code),
    custody_provider varchar(80) not null,
    key_alias varchar(160) not null,
    key_version varchar(80) not null,
    algorithm varchar(40) not null,
    latency_ms bigint not null,
    success boolean not null,
    error_reason varchar(500),
    occurred_at timestamptz not null,
    constraint ck_wallet_custody_signing_audit_asset_upper check (asset_code = upper(asset_code))
);

create index ix_wallet_custody_signing_audit_withdrawal
    on wallet_custody_signing_audit(withdrawal_id, occurred_at desc);

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
            'WITHDRAWAL_QUEUED',
            'WITHDRAWAL_QUEUE_TRANSITIONED',
            'WITHDRAWAL_APPROVED',
            'WITHDRAWAL_REJECTED',
            'WITHDRAWAL_BROADCAST_RECORDED',
            'WITHDRAWAL_CONFIRMED',
            'CUSTODY_KEY_ROTATED',
            'CUSTODY_SIGNING_STARTED',
            'CUSTODY_SIGNING_SUCCEEDED',
            'CUSTODY_SIGNING_FAILED',
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    );
