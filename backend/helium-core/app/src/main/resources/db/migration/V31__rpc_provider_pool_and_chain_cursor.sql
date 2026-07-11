alter table wallet_chain_monitor_states
    add column if not exists scan_checkpoint_block_height bigint not null default 0,
    add column if not exists last_successful_provider varchar(160),
    add column if not exists last_observed_block_hash varchar(160),
    add column if not exists last_observed_parent_hash varchar(160),
    add column if not exists deep_reorg_review_required boolean not null default false;

update wallet_chain_monitor_states
set scan_checkpoint_block_height = last_observed_block_height
where scan_checkpoint_block_height = 0 and last_observed_block_height > 0;

alter table wallet_chain_monitor_states
    drop constraint if exists ck_wallet_chain_monitor_scan_checkpoint;

alter table wallet_chain_monitor_states
    add constraint ck_wallet_chain_monitor_scan_checkpoint
        check (scan_checkpoint_block_height >= 0 and scan_checkpoint_block_height <= last_observed_block_height);

create table if not exists wallet_rpc_provider_health (
    provider_id varchar(160) primary key,
    chain varchar(40) not null,
    url_hash varchar(64) not null,
    manually_disabled boolean not null default false,
    health_state varchar(30) not null,
    circuit_state varchar(30) not null,
    latest_block_height bigint,
    consecutive_failures integer not null default 0,
    request_count bigint not null default 0,
    failure_count bigint not null default 0,
    timeout_count bigint not null default 0,
    last_latency_ms bigint,
    last_success_at timestamptz,
    last_failure_at timestamptz,
    retry_after_at timestamptz,
    disabled_at timestamptz,
    last_consistency_issue varchar(500),
    updated_at timestamptz not null,
    constraint ck_wallet_rpc_provider_health_chain_upper check (chain = upper(chain)),
    constraint ck_wallet_rpc_provider_health_state check (health_state in ('HEALTHY', 'DEGRADED', 'UNAVAILABLE')),
    constraint ck_wallet_rpc_provider_circuit_state check (circuit_state in ('CLOSED', 'OPEN', 'HALF_OPEN'))
);

create index if not exists ix_wallet_rpc_provider_health_chain
    on wallet_rpc_provider_health(chain, health_state);
