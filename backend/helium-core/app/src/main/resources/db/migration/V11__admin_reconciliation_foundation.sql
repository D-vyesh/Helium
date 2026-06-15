create table admin_audit_events (
    id uuid primary key,
    action varchar(80) not null,
    actor_id varchar(120) not null,
    target_type varchar(80) not null,
    target_id varchar(160) not null,
    details varchar(1000) not null,
    occurred_at timestamptz not null,
    constraint ck_admin_audit_action check (
        action in (
            'USER_STATUS_CHANGED',
            'ROLE_GRANTED',
            'ROLE_REVOKED',
            'WITHDRAWAL_APPROVED',
            'WITHDRAWAL_REJECTED',
            'MARKET_UPDATED',
            'FEE_SCHEDULE_UPDATED',
            'TRADING_HALTED',
            'TRADING_RESUMED',
            'RECONCILIATION_REPORT_CREATED',
            'MANUAL_RECONCILIATION_OPENED',
            'MANUAL_RECONCILIATION_RESOLVED',
            'CSV_EXPORTED'
        )
    )
);

create table admin_reconciliation_reports (
    id uuid primary key,
    report_type varchar(80) not null,
    status varchar(40) not null,
    business_date date not null,
    scope_key varchar(160) not null,
    left_label varchar(80) not null,
    right_label varchar(80) not null,
    left_total numeric(38, 18) not null,
    right_total numeric(38, 18) not null,
    difference numeric(38, 18) not null,
    created_by varchar(120) not null,
    created_at timestamptz not null,
    constraint ck_admin_recon_report_type check (
        report_type in ('LEDGER_WALLET', 'WALLET_CHAIN', 'TRADING_SETTLEMENT', 'MATCHING_EXECUTION', 'DAILY_BALANCE')
    ),
    constraint ck_admin_recon_report_status check (status in ('CLEAN', 'DISCREPANCY')),
    constraint ck_admin_recon_report_difference check (difference = left_total - right_total),
    constraint ck_admin_recon_report_status_difference check (
        (status = 'CLEAN' and difference = 0)
        or (status = 'DISCREPANCY' and difference <> 0)
    )
);

create table admin_reconciliation_discrepancies (
    id uuid primary key,
    report_id uuid not null references admin_reconciliation_reports(id),
    report_type varchar(80) not null,
    severity varchar(40) not null,
    scope_key varchar(160) not null,
    expected_total numeric(38, 18) not null,
    actual_total numeric(38, 18) not null,
    difference numeric(38, 18) not null,
    details varchar(1000) not null,
    detected_at timestamptz not null,
    constraint ck_admin_recon_discrepancy_type check (
        report_type in ('LEDGER_WALLET', 'WALLET_CHAIN', 'TRADING_SETTLEMENT', 'MATCHING_EXECUTION', 'DAILY_BALANCE')
    ),
    constraint ck_admin_recon_discrepancy_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint ck_admin_recon_discrepancy_difference check (difference = expected_total - actual_total),
    constraint ck_admin_recon_discrepancy_nonzero check (difference <> 0)
);

create table admin_daily_balance_snapshots (
    id uuid primary key,
    business_date date not null,
    asset_code varchar(32) not null,
    ledger_total numeric(38, 18) not null,
    wallet_total numeric(38, 18) not null,
    trading_locked_total numeric(38, 18) not null,
    created_by varchar(120) not null,
    created_at timestamptz not null,
    constraint ck_admin_daily_balance_asset_upper check (asset_code = upper(asset_code))
);

create table admin_manual_reconciliation_cases (
    id uuid primary key,
    discrepancy_id uuid not null references admin_reconciliation_discrepancies(id),
    status varchar(40) not null,
    opened_by varchar(120) not null,
    opened_at timestamptz not null,
    resolution_notes varchar(1000),
    resolved_by varchar(120),
    resolved_at timestamptz,
    version bigint not null default 0,
    constraint ck_admin_manual_recon_status check (status in ('OPEN', 'RESOLVED')),
    constraint ck_admin_manual_recon_lifecycle check (
        (status = 'OPEN' and resolution_notes is null and resolved_by is null and resolved_at is null)
        or (status = 'RESOLVED' and resolution_notes is not null and resolved_by is not null and resolved_at is not null)
    )
);

create index ix_admin_audit_events_time on admin_audit_events(occurred_at desc);
create index ix_admin_recon_reports_business_date on admin_reconciliation_reports(business_date, created_at desc);
create index ix_admin_recon_reports_type on admin_reconciliation_reports(report_type, created_at desc);
create index ix_admin_recon_discrepancies_report on admin_reconciliation_discrepancies(report_id);
create index ix_admin_daily_balance_date_asset on admin_daily_balance_snapshots(business_date, asset_code);
create index ix_admin_manual_recon_status on admin_manual_reconciliation_cases(status);

create or replace function admin_reject_append_only_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'admin table % is append-only after insert', tg_table_name;
end;
$$;

create trigger trg_admin_audit_events_append_only
before update or delete on admin_audit_events
for each row execute function admin_reject_append_only_mutation();

create trigger trg_admin_reconciliation_reports_append_only
before update or delete on admin_reconciliation_reports
for each row execute function admin_reject_append_only_mutation();

create trigger trg_admin_reconciliation_discrepancies_append_only
before update or delete on admin_reconciliation_discrepancies
for each row execute function admin_reject_append_only_mutation();

create trigger trg_admin_daily_balance_snapshots_append_only
before update or delete on admin_daily_balance_snapshots
for each row execute function admin_reject_append_only_mutation();
