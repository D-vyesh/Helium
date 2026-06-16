package com.helium.core.authuser.domain;

import java.util.Set;

public enum Role {
    USER(Set.of()),
    ADMIN(Set.of(Permission.values())),
    TREASURY_ADMIN(Set.of(Permission.VIEW_FINANCE_DASHBOARDS, Permission.INITIATE_COLD_SWEEP, Permission.APPROVE_COLD_SWEEP)),
    SECURITY_ADMIN(Set.of(Permission.MANAGE_SECURITY_POLICIES, Permission.INITIATE_ACCOUNT_FREEZE, Permission.APPROVE_ACCOUNT_FREEZE)),
    COMPLIANCE_OFFICER(Set.of(Permission.GENERATE_REGULATORY_REPORTS, Permission.INITIATE_ACCOUNT_FREEZE, Permission.APPROVE_ACCOUNT_FREEZE)),
    SUPPORT_AGENT(Set.of()),
    AUDITOR(Set.of(Permission.VIEW_AUDIT_LOGS, Permission.VIEW_FINANCE_DASHBOARDS)),
    RISK_MANAGER(Set.of(Permission.VIEW_FINANCE_DASHBOARDS));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
