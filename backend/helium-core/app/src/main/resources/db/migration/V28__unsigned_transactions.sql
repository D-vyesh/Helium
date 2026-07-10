alter table wallet_withdrawal_queue
    drop constraint if exists ck_wallet_withdrawal_queue_status;

alter table wallet_withdrawal_queue
    add constraint ck_wallet_withdrawal_queue_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_withdrawal_queue_transitions
    drop constraint if exists ck_wallet_withdrawal_queue_transitions_status;

alter table wallet_withdrawal_queue_transitions
    add constraint ck_wallet_withdrawal_queue_transitions_status check (
        status in (
            'REQUESTED', 'VALIDATING', 'APPROVED', 'WAITING_SIGN', 'BUILDING_TRANSACTION', 'BUILD_FAILED',
            'TRANSACTION_BUILT', 'WAITING_SIGNER', 'SIGNING', 'SIGNED', 'WAITING_BROADCAST',
            'BROADCASTING', 'BROADCASTED', 'PENDING_CONFIRMATIONS', 'CONFIRMED', 'FAILED', 'CANCELLED'
        )
    );

alter table wallet_withdrawal_queue
    add column build_attempts integer not null default 0,
    add column next_build_attempt_at timestamptz;

create table wallet_unsigned_transactions (
    id uuid primary key,
    withdrawal_id uuid not null references wallet_withdrawals(id),
    asset_code varchar(32) not null references wallet_assets(code),
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    format varchar(40) not null,
    builder_version varchar(40) not null,
    serialized_payload text not null,
    psbt text,
    nonce bigint,
    recent_blockhash varchar(128),
    fee numeric(38, 18) not null,
    metadata text not null,
    built_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_wallet_unsigned_transactions_withdrawal unique (withdrawal_id),
    constraint ck_wallet_unsigned_transactions_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_wallet_unsigned_transactions_network_upper check (network_code = upper(network_code)),
    constraint ck_wallet_unsigned_transactions_fee check (fee >= 0),
    constraint ck_wallet_unsigned_transactions_btc check (
        (asset_code <> 'BTC') or (format = 'PSBT' and psbt is not null and nonce is null and recent_blockhash is null)
    ),
    constraint ck_wallet_unsigned_transactions_eth check (
        (asset_code <> 'ETH') or (format = 'EIP1559_JSON' and psbt is null and nonce is not null and recent_blockhash is null)
    ),
    constraint ck_wallet_unsigned_transactions_sol check (
        (asset_code <> 'SOL') or (format = 'SOLANA_V0' and psbt is null and nonce is null and recent_blockhash is not null)
    )
);

create index ix_wallet_unsigned_transactions_network_built
    on wallet_unsigned_transactions(network_code, built_at desc);
