alter table wallet_deposits
    add column if not exists reorged_at timestamptz,
    add column if not exists failure_reason varchar(120);

alter table wallet_deposits
    drop constraint if exists ck_wallet_deposits_status,
    drop constraint if exists ck_wallet_deposits_lifecycle;

update wallet_deposits
set status = case status
    when 'POSTED' then 'POSTED_TO_LEDGER'
    when 'REJECTED' then 'FAILED'
    else status
end;

update wallet_deposits
set failure_reason = 'LEGACY_REJECTED'
where status = 'FAILED'
  and failure_reason is null;

alter table wallet_deposits
    add constraint ck_wallet_deposits_status check (
        status in ('DETECTED', 'PENDING_CONFIRMATIONS', 'CONFIRMED', 'POSTED_TO_LEDGER', 'FAILED', 'REORGED')
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
        or (status = 'FAILED'
            and posted_at is null
            and ledger_transaction_id is null
            and reorged_at is null
            and failure_reason is not null)
        or (status = 'REORGED'
            and reorged_at is not null
            and failure_reason is not null)
    );

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
            'WITHDRAWAL_APPROVED',
            'WITHDRAWAL_REJECTED',
            'WITHDRAWAL_BROADCAST_RECORDED',
            'WITHDRAWAL_CONFIRMED',
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    );
