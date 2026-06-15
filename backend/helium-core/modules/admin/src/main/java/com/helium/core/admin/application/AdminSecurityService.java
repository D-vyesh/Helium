package com.helium.core.admin.application;

import com.helium.core.admin.domain.AdminValidationException;
import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.domain.Role;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminSecurityService {
    private static final Set<Role> ADMIN_ROLES = Set.of(Role.ADMIN, Role.FINANCE_OPS, Role.COMPLIANCE);

    private final TrustedActorProvider trustedActorProvider;
    private final AuthorizationPort authorizationPort;

    public AdminSecurityService(TrustedActorProvider trustedActorProvider, AuthorizationPort authorizationPort) {
        this.trustedActorProvider = trustedActorProvider;
        this.authorizationPort = authorizationPort;
    }

    public String requireAdminActor() {
        UUID actorId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new AdminValidationException("authenticated admin actor is required"));
        boolean authorized = ADMIN_ROLES.stream().anyMatch(role -> authorizationPort.hasRole(actorId, role));
        if (!authorized) {
            throw new AdminValidationException("admin operation is not authorized");
        }
        return actorId.toString();
    }

    public String currentActorId() {
        return trustedActorProvider.currentActorId();
    }
}
