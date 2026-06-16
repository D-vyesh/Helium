-- Flyway migration for enterprise roles and governance

-- Add new roles to auth_role_grants
alter table auth_role_grants drop constraint ck_auth_role_grants_role;
alter table auth_role_grants add constraint ck_auth_role_grants_role check (
    role in ('USER', 'ADMIN', 'FINANCE_OPS', 'SUPPORT', 'COMPLIANCE', 'AUDITOR', 
             'TREASURY_ADMIN', 'SECURITY_ADMIN', 'COMPLIANCE_OFFICER', 'SUPPORT_AGENT', 'RISK_MANAGER')
);

-- Governance Approvals Table
create table governance_approval_requests (
    id uuid primary key,
    request_type varchar(60) not null,
    status varchar(40) not null,
    maker_id varchar(120) not null,
    checker_role varchar(60) not null,
    checker_id varchar(120),
    payload jsonb not null,
    created_at timestamp with time zone not null,
    resolved_at timestamp with time zone
);

create index idx_governance_approval_requests_status on governance_approval_requests (status);
