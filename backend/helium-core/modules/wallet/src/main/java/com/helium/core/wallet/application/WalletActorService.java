package com.helium.core.wallet.application;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.application.TrustedSystemActorProvider;
import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import com.helium.core.wallet.domain.WalletValidationException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WalletActorService {
    private static final Set<Role> OPERATIONS_ROLES = Set.of(Role.ADMIN, Role.FINANCE_OPS, Role.COMPLIANCE);

    private final TrustedActorProvider trustedActorProvider;
    private final TrustedSystemActorProvider trustedSystemActorProvider;
    private final AuthorizationPort authorizationPort;

    public WalletActorService(
        TrustedActorProvider trustedActorProvider,
        TrustedSystemActorProvider trustedSystemActorProvider,
        AuthorizationPort authorizationPort
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.trustedSystemActorProvider = trustedSystemActorProvider;
        this.authorizationPort = authorizationPort;
    }

    public UUID requireCurrentUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new WalletValidationException("authenticated wallet actor is required"));
    }

    public String requireCurrentUserActorId() {
        return requireCurrentUserId().toString();
    }

    public String requireOperationsActor() {
        UUID actorId = requireCurrentUserId();
        boolean authorized = OPERATIONS_ROLES.stream().anyMatch(role -> authorizationPort.hasRole(actorId, role));
        if (!authorized) {
            throw new WalletValidationException("wallet operation is not authorized");
        }
        return actorId.toString();
    }

    public String requireChainMonitorActor() {
        try {
            trustedSystemActorProvider.requireChainMonitor();
            return trustedSystemActorProvider.chainMonitorActorId();
        } catch (AuthValidationException exception) {
            throw new WalletValidationException(exception.getMessage());
        }
    }
}
