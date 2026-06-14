create table wallet_assets (
    code varchar(32) primary key,
    name varchar(120) not null,
    scale integer not null,
    deposit_enabled boolean not null,
    withdrawal_enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_wallet_assets_code_upper check (code = upper(code)),
    constraint ck_wallet_assets_scale check (scale between 0 and 18)
);

create table wallet_blockchain_networks (
    network_code varchar(40) primary key,
    asset_code varchar(32) not null references wallet_assets(code),
    display_name varchar(120) not null,
    required_confirmations integer not null,
    deposit_enabled boolean not null,
    withdrawal_enabled boolean not null,
    minimum_withdrawal numeric(38, 18) not null,
    withdrawal_fee numeric(38, 18) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_wallet_networks_code_upper check (network_code = upper(network_code)),
    constraint ck_wallet_networks_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_networks_confirmations check (required_confirmations > 0),
    constraint ck_wallet_networks_minimum check (minimum_withdrawal >= 0),
    constraint ck_wallet_networks_fee check (withdrawal_fee >= 0)
);

create table wallet_deposit_addresses (
    id uuid primary key,
    user_id uuid not null,
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    address varchar(160) not null,
    memo varchar(120),
    status varchar(40) not null,
    created_at timestamptz not null,
    constraint uk_wallet_deposit_addresses_user_asset_network unique (user_id, asset_code, network_code),
    constraint uk_wallet_deposit_addresses_network_address unique (network_code, address),
    constraint ck_wallet_deposit_addresses_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_deposit_addresses_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_deposit_addresses_status check (status in ('ACTIVE', 'RETIRED'))
);

create table wallet_deposits (
    id uuid primary key,
    user_id uuid not null,
    deposit_address_id uuid not null references wallet_deposit_addresses(id),
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    tx_hash varchar(160) not null,
    output_index integer not null,
    amount numeric(38, 18) not null,
    confirmations integer not null,
    required_confirmations integer not null,
    status varchar(40) not null,
    ledger_transaction_id uuid references ledger_transactions(id),
    detected_at timestamptz not null,
    confirmed_at timestamptz,
    posted_at timestamptz,
    version bigint not null default 0,
    constraint uk_wallet_deposits_network_tx_output unique (network_code, tx_hash, output_index),
    constraint ck_wallet_deposits_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_deposits_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_deposits_output check (output_index >= 0),
    constraint ck_wallet_deposits_amount check (amount > 0),
    constraint ck_wallet_deposits_confirmations check (confirmations >= 0 and required_confirmations > 0),
    constraint ck_wallet_deposits_status check (status in ('DETECTED', 'CONFIRMED', 'POSTED', 'REJECTED')),
    constraint ck_wallet_deposits_lifecycle check (
        (status = 'DETECTED' and confirmed_at is null and posted_at is null and ledger_transaction_id is null)
        or (status = 'CONFIRMED' and confirmed_at is not null and posted_at is null and ledger_transaction_id is null)
        or (status = 'POSTED' and confirmed_at is not null and posted_at is not null and ledger_transaction_id is not null)
        or (status = 'REJECTED' and posted_at is null and ledger_transaction_id is null)
    )
);

create table wallet_withdrawals (
    id uuid primary key,
    client_request_id varchar(120) not null,
    request_hash char(64) not null,
    user_id uuid not null,
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    destination_address varchar(160) not null,
    destination_memo varchar(120),
    amount numeric(38, 18) not null,
    fee numeric(38, 18) not null,
    reserve_ledger_transaction_id uuid not null references ledger_transactions(id),
    settlement_ledger_transaction_id uuid references ledger_transactions(id),
    release_ledger_transaction_id uuid references ledger_transactions(id),
    status varchar(40) not null,
    requested_at timestamptz not null,
    approved_by varchar(120),
    approved_at timestamptz,
    rejected_by varchar(120),
    rejected_at timestamptz,
    rejection_reason varchar(500),
    broadcast_tx_hash varchar(160),
    broadcasted_at timestamptz,
    confirmed_at timestamptz,
    version bigint not null default 0,
    constraint uk_wallet_withdrawals_user_client_request unique (user_id, client_request_id),
    constraint ck_wallet_withdrawals_request_hash check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_wallet_withdrawals_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_withdrawals_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_withdrawals_amount check (amount > 0),
    constraint ck_wallet_withdrawals_fee check (fee >= 0),
    constraint ck_wallet_withdrawals_status check (status in ('REQUESTED', 'APPROVED', 'REJECTED', 'BROADCASTED', 'CONFIRMED')),
    constraint ck_wallet_withdrawals_lifecycle check (
        (status = 'REQUESTED'
            and approved_at is null and rejected_at is null and broadcasted_at is null and confirmed_at is null
            and settlement_ledger_transaction_id is null and release_ledger_transaction_id is null)
        or (status = 'APPROVED'
            and approved_at is not null and approved_by is not null
            and rejected_at is null and broadcasted_at is null and confirmed_at is null
            and settlement_ledger_transaction_id is null and release_ledger_transaction_id is null)
        or (status = 'REJECTED'
            and rejected_at is not null and rejected_by is not null and rejection_reason is not null
            and release_ledger_transaction_id is not null
            and broadcasted_at is null and confirmed_at is null and settlement_ledger_transaction_id is null)
        or (status = 'BROADCASTED'
            and approved_at is not null and approved_by is not null
            and broadcasted_at is not null and broadcast_tx_hash is not null
            and confirmed_at is null and settlement_ledger_transaction_id is null and release_ledger_transaction_id is null)
        or (status = 'CONFIRMED'
            and approved_at is not null and approved_by is not null
            and broadcasted_at is not null and broadcast_tx_hash is not null
            and confirmed_at is not null and settlement_ledger_transaction_id is not null
            and release_ledger_transaction_id is null)
    )
);

create table wallet_broadcast_attempts (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    attempt_number integer not null,
    tx_hash varchar(160) not null,
    status varchar(40) not null,
    recorded_by varchar(120) not null,
    recorded_at timestamptz not null,
    constraint uk_wallet_broadcast_attempts_withdrawal_attempt unique (withdrawal_id, attempt_number),
    constraint uk_wallet_broadcast_attempts_tx_hash unique (tx_hash),
    constraint ck_wallet_broadcast_attempts_number check (attempt_number > 0),
    constraint ck_wallet_broadcast_attempts_status check (status in ('RECORDED', 'FAILED'))
);

create table wallet_chain_transaction_observations (
    id uuid primary key,
    direction varchar(40) not null,
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    tx_hash varchar(160) not null,
    output_index integer not null,
    address varchar(160) not null,
    memo varchar(120),
    amount numeric(38, 18) not null,
    confirmations integer not null,
    matched_deposit_id uuid references wallet_deposits(id),
    matched_withdrawal_id uuid references wallet_withdrawals(id),
    observed_by varchar(120) not null,
    observed_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_wallet_chain_observations_chain_ref unique (network_code, tx_hash, output_index),
    constraint uk_wallet_chain_observations_deposit unique (matched_deposit_id),
    constraint uk_wallet_chain_observations_withdrawal unique (matched_withdrawal_id),
    constraint ck_wallet_chain_observations_direction check (direction in ('DEPOSIT', 'WITHDRAWAL')),
    constraint ck_wallet_chain_observations_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_chain_observations_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_chain_observations_output check (output_index >= 0),
    constraint ck_wallet_chain_observations_amount check (amount > 0),
    constraint ck_wallet_chain_observations_confirmations check (confirmations >= 0),
    constraint ck_wallet_chain_observations_match check (
        (direction = 'DEPOSIT' and matched_deposit_id is not null and matched_withdrawal_id is null)
        or (direction = 'WITHDRAWAL' and matched_deposit_id is null and matched_withdrawal_id is not null)
    )
);

create table wallet_chain_monitor_states (
    network_code varchar(40) primary key references wallet_blockchain_networks(network_code),
    last_observed_block_height bigint not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_wallet_chain_monitor_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_chain_monitor_height check (last_observed_block_height >= 0)
);

create table wallet_reconciliation_discrepancies (
    id uuid primary key,
    discrepancy_type varchar(60) not null,
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    wallet_total numeric(38, 18) not null,
    ledger_total numeric(38, 18) not null,
    chain_total numeric(38, 18) not null,
    details varchar(500) not null,
    detected_at timestamptz not null,
    constraint ck_wallet_reconciliation_type check (
        discrepancy_type in ('WALLET_LEDGER_MISMATCH', 'CHAIN_WALLET_MISMATCH')
    ),
    constraint ck_wallet_reconciliation_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_reconciliation_network_upper check (network_code = upper(network_code))
);

create table wallet_audit_events (
    id uuid primary key,
    event_type varchar(60) not null,
    aggregate_id uuid,
    actor_id varchar(120) not null,
    details varchar(1000) not null,
    occurred_at timestamptz not null,
    constraint ck_wallet_audit_events_type check (
        event_type in (
            'ASSET_REGISTERED',
            'NETWORK_REGISTERED',
            'DEPOSIT_ADDRESS_ASSIGNED',
            'DEPOSIT_DETECTED',
            'DEPOSIT_CONFIRMATIONS_UPDATED',
            'DEPOSIT_POSTED',
            'WITHDRAWAL_REQUESTED',
            'WITHDRAWAL_APPROVED',
            'WITHDRAWAL_REJECTED',
            'WITHDRAWAL_BROADCAST_RECORDED',
            'WITHDRAWAL_CONFIRMED',
            'CHAIN_MONITOR_UPDATED',
            'RECONCILIATION_CHECKED'
        )
    )
);

create index ix_wallet_deposit_addresses_user on wallet_deposit_addresses(user_id);
create index ix_wallet_deposits_user_status on wallet_deposits(user_id, status);
create index ix_wallet_deposits_network_status on wallet_deposits(network_code, status);
create index ix_wallet_withdrawals_user_status on wallet_withdrawals(user_id, status);
create index ix_wallet_withdrawals_network_status on wallet_withdrawals(network_code, status);
create index ix_wallet_chain_observations_asset_network on wallet_chain_transaction_observations(asset_code, network_code, direction);
create index ix_wallet_reconciliation_time on wallet_reconciliation_discrepancies(detected_at desc);
create index ix_wallet_audit_events_time on wallet_audit_events(occurred_at desc);

create function wallet_reject_audit_event_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'wallet audit events are immutable after insert';
end;
$$;

create trigger trg_wallet_audit_events_immutable
before update or delete on wallet_audit_events
for each row execute function wallet_reject_audit_event_mutation();

create function wallet_reject_reconciliation_discrepancy_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'wallet reconciliation discrepancy records are immutable after insert';
end;
$$;

create trigger trg_wallet_reconciliation_discrepancies_immutable
before update or delete on wallet_reconciliation_discrepancies
for each row execute function wallet_reject_reconciliation_discrepancy_mutation();
