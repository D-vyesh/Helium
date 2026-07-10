create table if not exists hd_wallet_chains (
    id uuid primary key,
    network_code varchar(40) not null references wallet_blockchain_networks(network_code),
    xpub varchar(255) not null,
    derivation_path varchar(255) not null,
    current_index integer not null default 0,
    version bigint not null default 0,
    constraint uk_hd_wallet_chains_network unique (network_code),
    constraint ck_hd_wallet_chains_network_upper check (network_code = upper(network_code)),
    constraint ck_hd_wallet_chains_index check (current_index >= 0)
);

alter table wallet_chain_monitor_states
    add column if not exists last_confirmed_block_height bigint not null default 0,
    add column if not exists reorg_checkpoint_block_height bigint not null default 0;

alter table wallet_chain_monitor_states
    add constraint ck_wallet_chain_monitor_confirmed_height
        check (last_confirmed_block_height >= 0 and last_confirmed_block_height <= last_observed_block_height),
    add constraint ck_wallet_chain_monitor_reorg_checkpoint
        check (reorg_checkpoint_block_height >= 0 and reorg_checkpoint_block_height <= last_observed_block_height);

insert into wallet_assets (code, name, scale, deposit_enabled, withdrawal_enabled, created_at, updated_at)
values
    ('BTC', 'Bitcoin', 8, true, true, now(), now()),
    ('ETH', 'Ethereum', 18, true, true, now(), now()),
    ('SOL', 'Solana', 9, true, true, now(), now())
on conflict (code) do update
    set deposit_enabled = excluded.deposit_enabled,
        withdrawal_enabled = excluded.withdrawal_enabled,
        updated_at = excluded.updated_at;

insert into wallet_blockchain_networks (
    network_code, asset_code, display_name, required_confirmations,
    deposit_enabled, withdrawal_enabled, minimum_withdrawal, withdrawal_fee,
    created_at, updated_at
)
values
    ('BTC', 'BTC', 'Bitcoin', 6, true, true, 0.000100000000000000, 0.000010000000000000, now(), now()),
    ('ETH', 'ETH', 'Ethereum', 12, true, true, 0.001000000000000000, 0.000500000000000000, now(), now()),
    ('SOL', 'SOL', 'Solana', 32, true, true, 0.010000000000000000, 0.000005000000000000, now(), now())
on conflict (network_code) do update
    set required_confirmations = excluded.required_confirmations,
        deposit_enabled = excluded.deposit_enabled,
        withdrawal_enabled = excluded.withdrawal_enabled,
        minimum_withdrawal = excluded.minimum_withdrawal,
        withdrawal_fee = excluded.withdrawal_fee,
        updated_at = excluded.updated_at;
