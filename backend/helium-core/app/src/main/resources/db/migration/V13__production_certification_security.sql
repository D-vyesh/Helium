alter table api_keys
    add column if not exists scopes text not null default 'read',
    add column if not exists expires_at timestamptz,
    add column if not exists secret_version varchar(64) not null default 'v1',
    add column if not exists rotated_from_key_id varchar(64);

alter table api_keys
    add constraint chk_api_keys_scopes_not_empty check (length(scopes) > 0),
    add constraint chk_api_keys_expires_after_create check (expires_at is null or expires_at > created_at);

create table api_key_nonces (
    key_id varchar(64) not null references api_keys(key_id),
    nonce varchar(128) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    primary key (key_id, nonce)
);

create index idx_api_key_nonces_expires_at on api_key_nonces(expires_at);
