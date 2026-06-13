package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.SessionStatus;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.UserSession;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import com.helium.core.authuser.infrastructure.UserSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService implements SessionPort {
    private final UserSessionRepository sessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final AuthorizationPort authorizationPort;
    private final TrustedActorProvider trustedActorProvider;
    private final TokenCodec tokenCodec;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public SessionService(
        UserSessionRepository sessionRepository,
        UserAccountRepository userAccountRepository,
        RoleGrantRepository roleGrantRepository,
        AuthorizationPort authorizationPort,
        TrustedActorProvider trustedActorProvider,
        TokenCodec tokenCodec,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.authorizationPort = authorizationPort;
        this.trustedActorProvider = trustedActorProvider;
        this.tokenCodec = tokenCodec;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<SessionView> validate(String rawToken) {
        String tokenHash = tokenCodec.hash(rawToken);
        Optional<UserSession> readSession = sessionRepository.findReadByTokenHash(tokenHash);
        if (readSession.isEmpty()) {
            return Optional.empty();
        }
        UUID userId = readSession.get().userId();
        UserAccount account = userAccountRepository.findByIdForUpdate(userId).orElse(null);
        if (account == null) {
            return Optional.empty();
        }
        UserSession session = sessionRepository.findByTokenHash(tokenHash).orElse(null);
        if (session == null || !session.userId().equals(userId)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (!account.canAuthenticate(now) || !session.isActive(now)) {
            return Optional.empty();
        }
        session.touch(now);
        Set<Role> roles = roleGrantRepository.findAllByUserIdAndRevokedAtIsNull(session.userId()).stream()
            .map(grant -> grant.role())
            .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new SessionView(session.id(), session.userId(), session.expiresAt(), roles));
    }

    @Override
    @Transactional
    public void logout(String rawToken, SecurityContextData securityContext) {
        sessionRepository.findByTokenHash(tokenCodec.hash(rawToken)).ifPresent(session -> {
            session.revoke("logout", clock.instant());
            auditService.record(SecurityAuditEventType.LOGOUT, session.userId(), session.id(), securityContext, "session logged out");
        });
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId, String reason, SecurityContextData securityContext) {
        UUID actorId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new AuthValidationException("authenticated actor is required"));
        if (!actorId.equals(userId)) {
            authorizationPort.requireRole(actorId, Role.ADMIN);
        }
        revokeActiveSessions(userId, reason, securityContext);
    }

    void revokeActiveSessions(UUID userId, String reason, SecurityContextData securityContext) {
        userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        Instant now = clock.instant();
        sessionRepository.findAllByUserIdAndStatus(userId, SessionStatus.ACTIVE).forEach(session -> {
            session.revoke(reason, now);
            auditService.record(SecurityAuditEventType.SESSION_REVOKED, userId, session.id(), securityContext, reason);
        });
    }
}
