alter table wallet_withdrawal_queue
    drop constraint if exists ck_wallet_withdrawal_queue_status;

alter table wallet_withdrawal_queue
    add constraint ck_wallet_withdrawal_queue_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'SIGN_FAILED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCAST_FAILED', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMING',
            'CONFIRMATION_FAILED', 'REORG_DETECTED', 'CHAIN_REVIEW_REQUIRED', 'CONFIRMED', 'FAILED', 'CANCELLED'
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
            'CONFIRMATION_FAILED', 'REORG_DETECTED', 'CHAIN_REVIEW_REQUIRED', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_deposits
    drop constraint if exists ck_wallet_deposits_status,
    drop constraint if exists ck_wallet_deposits_lifecycle;

alter table wallet_deposits
    add constraint ck_wallet_deposits_status check (
        status in ('DETECTED', 'PENDING_CONFIRMATIONS', 'CONFIRMED', 'POSTED_TO_LEDGER', 'CHAIN_REVIEW_REQUIRED', 'FAILED', 'REORGED')
    ),
    add constraint ck_wallet_deposits_lifecycle check (
        (status = 'DETECTED'
            and confirmed_at is null
            and posted_at is null
            and ledger_transaction_id is null
            and reorged_at is null
            and failure_reason is null)
        or (status = 'PENDING_CONFIRMATIONS'
            and confirmed_at is null
            and posted_at is null
            and ledger_transaction_id is null
            and reorged_at is null
            and failure_reason is null)
        or (status = 'CONFIRMED'
            and confirmed_at is not null
            and posted_at is null
            and ledger_transaction_id is null
            and reorged_at is null
            and failure_reason is null)
        or (status = 'POSTED_TO_LEDGER'
            and confirmed_at is not null
            and posted_at is not null
            and ledger_transaction_id is not null
            and reorged_at is null
            and failure_reason is null)
        or (status = 'CHAIN_REVIEW_REQUIRED'
            and failure_reason is not null)
        or (status = 'FAILED'
            and posted_at is null
            and ledger_transaction_id is null
            and reorged_at is null
            and failure_reason is not null)
        or (status = 'REORGED'
            and reorged_at is not null
            and failure_reason is not null)
    );

create table blockchain_consistency_incidents (
    id uuid primary key,
    network varchar(40) not null,
    transaction_id varchar(160) not null,
    incident_type varchar(60) not null,
    expected_state varchar(80) not null,
    provider_observations_json text not null,
    status varchar(30) not null,
    detected_at timestamptz not null,
    resolved_at timestamptz,
    resolved_by varchar(120),
    resolution_notes varchar(1000),
    version bigint not null default 0,
    constraint ck_blockchain_consistency_incidents_network_upper check (network = upper(network)),
    constraint ck_blockchain_consistency_incidents_status check (status in ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    constraint ck_blockchain_consistency_incidents_resolution check (
        (status = 'OPEN' and resolved_at is null and resolved_by is null and resolution_notes is null)
        or (status in ('ACKNOWLEDGED', 'RESOLVED') and resolved_at is not null and resolved_by is not null and resolution_notes is not null)
    )
);

create index ix_blockchain_consistency_incidents_status
    on blockchain_consistency_incidents(status, detected_at desc);

create index ix_blockchain_consistency_incidents_transaction
    on blockchain_consistency_incidents(network, transaction_id, incident_type, status);

create table blockchain_canonical_blocks (
    id uuid primary key,
    network varchar(40) not null,
    height bigint not null,
    block_hash varchar(160) not null,
    parent_hash varchar(160),
    provider_consensus varchar(30) not null,
    observed_at timestamptz not null,
    constraint uk_blockchain_canonical_blocks_network_height unique (network, height),
    constraint ck_blockchain_canonical_blocks_network_upper check (network = upper(network)),
    constraint ck_blockchain_canonical_blocks_height check (height >= 0),
    constraint ck_blockchain_canonical_blocks_consensus check (
        provider_consensus in ('AGREED', 'INSUFFICIENT_PROVIDERS', 'PROVIDER_DISAGREEMENT', 'TRANSACTION_NOT_FOUND', 'REORG_SUSPECTED')
    )
);

create index ix_blockchain_canonical_blocks_network_observed
    on blockchain_canonical_blocks(network, observed_at desc);

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
            'WITHDRAWAL_CHAIN_REVIEW_REQUIRED',
            'WITHDRAWAL_STUCK_TRANSACTION_DETECTED',
            'WITHDRAWAL_CONFIRMED',
            'DEPOSIT_CHAIN_REVIEW_REQUIRED',
            'RPC_PROVIDER_DISAGREEMENT',
            'DEEP_REORG_DETECTED',
            'BLOCKCHAIN_INCIDENT_ACKNOWLEDGED',
            'BLOCKCHAIN_INCIDENT_RESOLVED',
            'CUSTODY_KEY_ROTATED',
            'CUSTODY_SIGNING_STARTED',
            'CUSTODY_SIGNING_SUCCEEDED',
            'CUSTODY_SIGNING_FAILED',
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    );
