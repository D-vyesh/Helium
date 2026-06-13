create table ledger_accounts (
    id uuid primary key,
    owner_type varchar(40) not null,
    owner_id varchar(120) not null,
    asset_code varchar(32) not null,
    balance_type varchar(40) not null,
    status varchar(40) not null,
    negative_balance_allowed boolean not null default false,
    created_at timestamptz not null,
    constraint uk_ledger_accounts_owner_asset_balance unique (owner_type, owner_id, asset_code, balance_type),
    constraint ck_ledger_accounts_asset_upper check (asset_code = upper(asset_code)),
    constraint ck_ledger_accounts_status check (status in ('ACTIVE', 'CLOSED')),
    constraint ck_ledger_accounts_owner_type check (owner_type in ('USER', 'EXCHANGE', 'FEE', 'CLEARING', 'EXTERNAL', 'SUSPENSE')),
    constraint ck_ledger_accounts_balance_type check (balance_type in ('AVAILABLE', 'LOCKED', 'CLEARING', 'FEE', 'EXTERNAL', 'SUSPENSE'))
);

create table ledger_transactions (
    id uuid primary key,
    transaction_type varchar(60) not null,
    business_reference varchar(160) not null,
    idempotency_key varchar(160) not null,
    description varchar(500) not null,
    actor_id varchar(120) not null,
    source_module varchar(80) not null,
    correlation_id varchar(120) not null,
    causation_id varchar(120) not null,
    audit_reason varchar(500) not null,
    created_at timestamptz not null,
    constraint uk_ledger_transactions_idempotency_key unique (idempotency_key),
    constraint ck_ledger_transactions_type check (
        transaction_type in ('GENERAL', 'INTERNAL_TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'FEE', 'TRADE_SETTLEMENT', 'REVERSAL', 'ADJUSTMENT')
    )
);

create table ledger_posting_lines (
    id uuid primary key,
    transaction_id uuid not null references ledger_transactions(id),
    account_id uuid not null references ledger_accounts(id),
    asset_code varchar(32) not null,
    direction varchar(20) not null,
    amount numeric(38, 18) not null,
    constraint ck_ledger_posting_lines_direction check (direction in ('DEBIT', 'CREDIT')),
    constraint ck_ledger_posting_lines_amount_positive check (amount > 0),
    constraint ck_ledger_posting_lines_asset_upper check (asset_code = upper(asset_code))
);

create table ledger_balance_snapshots (
    id uuid primary key,
    account_id uuid not null references ledger_accounts(id),
    asset_code varchar(32) not null,
    current_balance numeric(38, 18) not null,
    version bigint not null,
    updated_at timestamptz not null,
    constraint uk_ledger_balance_snapshots_account unique (account_id),
    constraint ck_ledger_balance_snapshots_asset_upper check (asset_code = upper(asset_code))
);

create table ledger_idempotency_records (
    idempotency_key varchar(160) primary key,
    transaction_id uuid not null references ledger_transactions(id),
    request_hash char(64) not null,
    status varchar(40) not null,
    created_at timestamptz not null,
    constraint uk_ledger_idempotency_records_transaction unique (transaction_id),
    constraint ck_ledger_idempotency_records_request_hash check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_ledger_idempotency_records_status check (status in ('POSTED'))
);

create index ix_ledger_posting_lines_transaction_id on ledger_posting_lines(transaction_id);
create index ix_ledger_posting_lines_account_id on ledger_posting_lines(account_id);
create index ix_ledger_transactions_business_reference on ledger_transactions(business_reference);
create index ix_ledger_balance_snapshots_asset_code on ledger_balance_snapshots(asset_code);

create function ledger_reject_immutable_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'ledger table % is immutable after insert', tg_table_name;
end;
$$;

create trigger trg_ledger_transactions_immutable
before update or delete on ledger_transactions
for each row execute function ledger_reject_immutable_mutation();

create trigger trg_ledger_posting_lines_immutable
before update or delete on ledger_posting_lines
for each row execute function ledger_reject_immutable_mutation();

create trigger trg_ledger_idempotency_records_immutable
before update or delete on ledger_idempotency_records
for each row execute function ledger_reject_immutable_mutation();

create function ledger_reject_negative_balance_policy_mutation()
returns trigger
language plpgsql
as $$
begin
    if new.negative_balance_allowed is distinct from old.negative_balance_allowed then
        raise exception 'ledger account negative balance policy is immutable after insert';
    end if;

    return new;
end;
$$;

create trigger trg_ledger_account_negative_balance_policy_immutable
before update of negative_balance_allowed on ledger_accounts
for each row execute function ledger_reject_negative_balance_policy_mutation();

create function ledger_assert_posting_line_account_asset()
returns trigger
language plpgsql
as $$
declare
    account_asset varchar(32);
begin
    select asset_code
    into account_asset
    from ledger_accounts
    where id = new.account_id;

    if account_asset is null then
        raise exception 'ledger posting line references a missing account %', new.account_id;
    end if;

    if account_asset <> new.asset_code then
        raise exception 'ledger posting asset % does not match account asset %', new.asset_code, account_asset;
    end if;

    return new;
end;
$$;

create trigger trg_ledger_posting_line_account_asset
before insert on ledger_posting_lines
for each row execute function ledger_assert_posting_line_account_asset();

create function ledger_assert_balance_snapshot_policy()
returns trigger
language plpgsql
as $$
declare
    account_negative_allowed boolean;
begin
    select negative_balance_allowed
    into account_negative_allowed
    from ledger_accounts
    where id = new.account_id;

    if account_negative_allowed is null then
        raise exception 'ledger balance snapshot references a missing account %', new.account_id;
    end if;

    if new.current_balance < 0 and account_negative_allowed = false then
        raise exception 'negative balance is not allowed for account %', new.account_id;
    end if;

    return new;
end;
$$;

create trigger trg_ledger_balance_snapshot_policy
before insert or update on ledger_balance_snapshots
for each row execute function ledger_assert_balance_snapshot_policy();

create function ledger_assert_transaction_balanced()
returns trigger
language plpgsql
as $$
declare
    checked_transaction_id uuid;
    line_count integer;
begin
    if tg_table_name = 'ledger_transactions' then
        checked_transaction_id := new.id;
    elsif tg_op = 'INSERT' then
        checked_transaction_id := new.transaction_id;
    else
        checked_transaction_id := old.transaction_id;
    end if;

    select count(*)
    into line_count
    from ledger_posting_lines
    where transaction_id = checked_transaction_id;

    if line_count < 2 then
        raise exception 'ledger transaction % must contain at least two posting lines', checked_transaction_id;
    end if;

    if exists (
        select 1
        from ledger_posting_lines
        where transaction_id = checked_transaction_id
        group by asset_code
        having sum(
            case direction
                when 'DEBIT' then amount
                when 'CREDIT' then -amount
            end
        ) <> 0
    ) then
        raise exception 'ledger transaction % is not balanced per asset', checked_transaction_id;
    end if;

    return null;
end;
$$;

create constraint trigger ctrg_ledger_transactions_balanced
after insert on ledger_transactions
deferrable initially deferred
for each row execute function ledger_assert_transaction_balanced();

create constraint trigger ctrg_ledger_posting_lines_balanced
after insert on ledger_posting_lines
deferrable initially deferred
for each row execute function ledger_assert_transaction_balanced();
