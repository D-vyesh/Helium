package com.helium.core.admin.application;

import com.helium.core.admin.domain.AdminAuditAction;
import com.helium.core.authuser.application.AccountAdministrationPort;
import com.helium.core.authuser.application.RoleManagementPort;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.domain.Role;
import com.helium.core.trading.application.MarketAdministrationPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationsService implements AdminOperationsPort {
    private static final SecurityContextData ADMIN_CONTEXT = new SecurityContextData("internal", "helium-admin-module");

    private final AdminSecurityService securityService;
    private final AdminAuditService auditService;
    private final AccountAdministrationPort accountAdministrationPort;
    private final RoleManagementPort roleManagementPort;
    private final MarketAdministrationPort marketAdministrationPort;

    public AdminOperationsService(
        AdminSecurityService securityService,
        AdminAuditService auditService,
        AccountAdministrationPort accountAdministrationPort,
        RoleManagementPort roleManagementPort,
        MarketAdministrationPort marketAdministrationPort
    ) {
        this.securityService = securityService;
        this.auditService = auditService;
        this.accountAdministrationPort = accountAdministrationPort;
        this.roleManagementPort = roleManagementPort;
        this.marketAdministrationPort = marketAdministrationPort;
    }

    @Override
    @Transactional
    public void suspendUser(UUID userId) {
        String actorId = securityService.requireAdminActor();
        accountAdministrationPort.suspend(userId, ADMIN_CONTEXT);
        auditService.record(AdminAuditAction.USER_STATUS_CHANGED, actorId, "USER", userId.toString(), "user suspended");
    }

    @Override
    @Transactional
    public void grantRole(UUID userId, Role role) {
        String actorId = securityService.requireAdminActor();
        roleManagementPort.grant(userId, role, ADMIN_CONTEXT);
        auditService.record(AdminAuditAction.ROLE_GRANTED, actorId, "USER", userId.toString(), role.name());
    }

    @Override
    @Transactional
    public void revokeRole(UUID userId, Role role) {
        String actorId = securityService.requireAdminActor();
        roleManagementPort.revoke(userId, role, ADMIN_CONTEXT);
        auditService.record(AdminAuditAction.ROLE_REVOKED, actorId, "USER", userId.toString(), role.name());
    }

    @Override
    @Transactional
    public void updateMarket(String symbol, boolean enabled) {
        String actorId = securityService.requireAdminActor();
        marketAdministrationPort.updateMarket(new MarketAdministrationPort.UpdateMarketCommand(symbol, enabled));
        auditService.record(
            enabled ? AdminAuditAction.TRADING_RESUMED : AdminAuditAction.TRADING_HALTED,
            actorId,
            "MARKET",
            symbol,
            enabled ? "market enabled" : "market disabled"
        );
    }
}
