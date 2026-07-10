create table wallet_withdrawal_queue (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    status varchar(40) not null,
    last_error varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_wallet_withdrawal_queue_withdrawal unique (withdrawal_id),
    constraint ck_wallet_withdrawal_queue_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'SIGNING', 'SIGNED',
            'WAITING_BROADCAST', 'BROADCASTING', 'BROADCASTED', 'PENDING_CONFIRMATIONS',
            'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    ),
    constraint ck_wallet_withdrawal_queue_error check (
        (status = 'FAILED' and last_error is not null)
        or (status <> 'FAILED' and last_error is null)
    )
);

create index ix_wallet_withdrawal_queue_status_updated on wallet_withdrawal_queue(status, updated_at);

create table wallet_withdrawal_queue_transitions (
    id uuid primary key,
    queue_id uuid not null references wallet_withdrawal_queue(id),
    status varchar(40) not null,
    actor_id varchar(120) not null,
    reason varchar(500) not null,
    occurred_at timestamptz not null,
    constraint ck_wallet_withdrawal_queue_transitions_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'SIGNING', 'SIGNED',
            'WAITING_BROADCAST', 'BROADCASTING', 'BROADCASTED', 'PENDING_CONFIRMATIONS',
            'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    )
);

create index ix_wallet_withdrawal_queue_transitions_queue_time
    on wallet_withdrawal_queue_transitions(queue_id, occurred_at);

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
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    );
