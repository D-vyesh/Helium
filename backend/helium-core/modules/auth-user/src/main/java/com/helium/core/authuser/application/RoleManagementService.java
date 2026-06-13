package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.RoleGrant;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService implements RoleManagementPort {
    private final UserAccountRepository userAccountRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final AuthorizationPort authorizationPort;
    private final SessionService sessionService;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public RoleManagementService(
        UserAccountRepository userAccountRepository,
        RoleGrantRepository roleGrantRepository,
        AuthorizationPort authorizationPort,
        SessionService sessionService,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.authorizationPort = authorizationPort;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void grant(UUID userId, Role role, SecurityContextData securityContext) {
        UUID actorId = authorizationPort.requireCurrentActor(Role.ADMIN);
        rejectSelfPrivilegeMutation(userId, role, actorId);
        lockUser(userId);
        if (roleGrantRepository.findByUserIdAndRoleAndRevokedAtIsNull(userId, role).isPresent()) {
            return;
        }
        roleGrantRepository.save(RoleGrant.grant(userId, role, actorId, clock.instant()));
        sessionService.revokeActiveSessions(userId, "roles changed", securityContext);
        auditService.record(SecurityAuditEventType.ROLE_GRANTED, userId, null, securityContext, role.name());
    }

    @Override
    @Transactional
    public void revoke(UUID userId, Role role, SecurityContextData securityContext) {
        UUID actorId = authorizationPort.requireCurrentActor(Role.ADMIN);
        rejectSelfPrivilegeMutation(userId, role, actorId);
        lockUser(userId);
        RoleGrant grant = roleGrantRepository.findByUserIdAndRoleAndRevokedAtIsNull(userId, role)
            .orElseThrow(() -> new AuthValidationException("active role grant was not found"));
        grant.revoke(actorId, clock.instant());
        sessionService.revokeActiveSessions(userId, "roles changed", securityContext);
        auditService.record(SecurityAuditEventType.ROLE_REVOKED, userId, null, securityContext, role.name());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Role> rolesFor(UUID userId) {
        return roleGrantRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
            .map(RoleGrant::role)
            .collect(Collectors.toUnmodifiableSet());
    }

    private void lockUser(UUID userId) {
        userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
    }

    private static void rejectSelfPrivilegeMutation(UUID userId, Role role, UUID actorId) {
        if (actorId.equals(userId) && role != Role.USER) {
            throw new AuthValidationException("actors cannot change their own privileged roles");
        }
    }
}
