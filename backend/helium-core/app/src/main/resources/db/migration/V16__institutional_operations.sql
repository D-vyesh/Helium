-- Flyway migration for institutional operations

-- Organizations for Corporate accounts
create table organizations (
    id uuid primary key,
    name varchar(120) not null,
    kyb_status varchar(60) not null,
    created_at timestamp with time zone not null
);

-- Support Tickets
create table support_tickets (
    id uuid primary key,
    user_id varchar(120) not null,
    subject varchar(255) not null,
    status varchar(60) not null,
    internal_notes text,
    created_at timestamp with time zone not null
);

create index idx_support_tickets_user_id on support_tickets (user_id);

-- Security Incidents (SOC)
create table security_incidents (
    id uuid primary key,
    title varchar(255) not null,
    severity varchar(20) not null,
    status varchar(60) not null,
    postmortem_report text,
    created_at timestamp with time zone not null
);

create index idx_security_incidents_status on security_incidents (status);
