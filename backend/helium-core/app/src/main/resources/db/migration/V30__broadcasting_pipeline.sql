alter table wallet_withdrawal_queue
    drop constraint if exists ck_wallet_withdrawal_queue_status;

alter table wallet_withdrawal_queue
    add constraint ck_wallet_withdrawal_queue_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'SIGN_FAILED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCAST_FAILED', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMING',
            'CONFIRMATION_FAILED', 'REORG_DETECTED', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_withdrawal_queue_transitions
    drop constraint if exists ck_wallet_withdrawal_queue_transitions_status;

alter table wallet_withdrawal_queue_transitions
    add constraint ck_wallet_withdrawal_queue_transitions_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'SIGN_FAILED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCAST_FAILED', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMING',
            'CONFIRMATION_FAILED', 'REORG_DETECTED', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_withdrawal_queue
    add column if not exists broadcast_attempts integer not null default 0,
    add column if not exists next_broadcast_attempt_at timestamptz,
    add column if not exists confirmation_failures integer not null default 0,
    add column if not exists next_confirmation_attempt_at timestamptz;

create table wallet_blockchain_broadcasts (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    signed_transaction_id uuid not null references wallet_signed_transactions(id),
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    attempt_number integer not null,
    tx_hash varchar(160),
    status varchar(30) not null,
    provider varchar(80) not null,
    node_id varchar(160) not null,
    fee_paid numeric(38, 18),
    rpc_latency_ms bigint,
    raw_response text,
    error_reason varchar(500),
    broadcasted_at timestamptz,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_wallet_blockchain_broadcasts_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_blockchain_broadcasts_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_blockchain_broadcasts_status check (status in ('BROADCASTED', 'FAILED')),
    constraint ck_wallet_blockchain_broadcasts_success_tx check (
        (status = 'BROADCASTED' and tx_hash is not null and broadcasted_at is not null and error_reason is null)
        or (status = 'FAILED' and tx_hash is null and error_reason is not null)
    )
);

create index ix_wallet_blockchain_broadcasts_withdrawal
    on wallet_blockchain_broadcasts(withdrawal_id, created_at desc);

create unique index uk_wallet_blockchain_broadcasts_success
    on wallet_blockchain_broadcasts(withdrawal_id)
    where status = 'BROADCASTED';

create table wallet_withdrawal_confirmations (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    tx_hash varchar(160) not null,
    confirmations integer not null,
    required_confirmations integer not null,
    status varchar(30) not null,
    first_seen_at timestamptz not null,
    last_checked_at timestamptz not null,
    confirmed_at timestamptz,
    failure_reason varchar(500),
    version bigint not null default 0,
    constraint uk_wallet_withdrawal_confirmations_withdrawal unique (withdrawal_id),
    constraint ck_wallet_withdrawal_confirmations_status check (status in ('CONFIRMING', 'CONFIRMED', 'REORG_DETECTED', 'FAILED')),
    constraint ck_wallet_withdrawal_confirmations_numbers check (
        confirmations >= 0 and required_confirmations > 0
    )
);

create index ix_wallet_withdrawal_confirmations_status
    on wallet_withdrawal_confirmations(status, last_checked_at asc);

create table wallet_withdrawal_reorg_events (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    tx_hash varchar(160) not null,
    previous_confirmations integer not null,
    current_confirmations integer not null,
    reason varchar(500) not null,
    manual_review_required boolean not null,
    detected_at timestamptz not null,
    constraint ck_wallet_withdrawal_reorg_events_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_withdrawal_reorg_events_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_withdrawal_reorg_events_confirmations check (
        previous_confirmations >= 0 and current_confirmations >= 0
    )
);

create index ix_wallet_withdrawal_reorg_events_detected
    on wallet_withdrawal_reorg_events(detected_at desc);

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
            'WITHDRAWAL_BROADCAST_SUBMITTED',
            'WITHDRAWAL_BROADCAST_FAILED',
            'WITHDRAWAL_CONFIRMATIONS_UPDATED',
            'WITHDRAWAL_CONFIRMATION_FAILED',
            'WITHDRAWAL_REORG_DETECTED',
            'WITHDRAWAL_STUCK_TRANSACTION_DETECTED',
            'WITHDRAWAL_CONFIRMED',
            'CUSTODY_KEY_ROTATED',
            'CUSTODY_SIGNING_STARTED',
            'CUSTODY_SIGNING_SUCCEEDED',
            'CUSTODY_SIGNING_FAILED',
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    );
