package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdministrationService implements AccountAdministrationPort {
    private final UserAccountRepository userAccountRepository;
    private final SessionService sessionService;
    private final AuthorizationPort authorizationPort;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public AccountAdministrationService(
        UserAccountRepository userAccountRepository,
        SessionService sessionService,
        AuthorizationPort authorizationPort,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.sessionService = sessionService;
        this.authorizationPort = authorizationPort;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void suspend(UUID userId, SecurityContextData securityContext) {
        requireAdministratorOtherThan(userId);
        UserAccount account = accountForUpdate(userId);
        account.suspend(clock.instant());
        sessionService.revokeActiveSessions(userId, "account suspended", securityContext);
        auditService.record(SecurityAuditEventType.ACCOUNT_SUSPENDED, userId, null, securityContext, "account suspended");
    }

    @Override
    @Transactional
    public void reactivate(UUID userId, SecurityContextData securityContext) {
        requireAdministratorOtherThan(userId);
        UserAccount account = accountForUpdate(userId);
        account.reactivate(clock.instant());
        auditService.record(SecurityAuditEventType.ACCOUNT_REACTIVATED, userId, null, securityContext, "account reactivated");
    }

    @Override
    @Transactional
    public void unlock(UUID userId, SecurityContextData securityContext) {
        requireAdministratorOtherThan(userId);
        UserAccount account = accountForUpdate(userId);
        account.unlock(clock.instant());
        auditService.record(SecurityAuditEventType.ACCOUNT_UNLOCKED, userId, null, securityContext, "account unlocked");
    }

    private UserAccount accountForUpdate(UUID userId) {
        return userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
    }

    private void requireAdministratorOtherThan(UUID userId) {
        UUID actorId = authorizationPort.requireCurrentActor(Role.ADMIN);
        if (actorId.equals(userId)) {
            throw new AuthValidationException("administrators cannot change their own account status");
        }
    }
}
