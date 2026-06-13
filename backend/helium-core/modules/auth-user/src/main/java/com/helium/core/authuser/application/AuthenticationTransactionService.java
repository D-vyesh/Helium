package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Credential;
import com.helium.core.authuser.domain.MfaStatus;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.UserAccountStatus;
import com.helium.core.authuser.domain.UserSession;
import com.helium.core.authuser.infrastructure.CredentialRepository;
import com.helium.core.authuser.infrastructure.MfaMethodRepository;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import com.helium.core.authuser.infrastructure.UserSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationTransactionService {
    private static final int MAXIMUM_FAILED_ATTEMPTS = 20;
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    private final UserAccountRepository userAccountRepository;
    private final CredentialRepository credentialRepository;
    private final UserSessionRepository sessionRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final MfaMethodRepository mfaMethodRepository;
    private final SessionService sessionService;
    private final LoginAttemptThrottleService throttleService;
    private final TokenCodec tokenCodec;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public AuthenticationTransactionService(
        UserAccountRepository userAccountRepository,
        CredentialRepository credentialRepository,
        UserSessionRepository sessionRepository,
        RoleGrantRepository roleGrantRepository,
        MfaMethodRepository mfaMethodRepository,
        SessionService sessionService,
        LoginAttemptThrottleService throttleService,
        TokenCodec tokenCodec,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.sessionRepository = sessionRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.mfaMethodRepository = mfaMethodRepository;
        this.sessionService = sessionService;
        this.throttleService = throttleService;
        this.tokenCodec = tokenCodec;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public LoginResult recordAnonymousFailure(String email, SecurityContextData context, String details) {
        throttleService.recordFailure(email, context.ipAddress());
        auditService.record(SecurityAuditEventType.LOGIN_FAILED, null, null, context, details);
        return LoginResult.failed(LoginFailureReason.AUTHENTICATION_FAILED);
    }

    @Transactional
    public LoginResult recordFailedLogin(UUID userId, String email, SecurityContextData context) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalStateException("user account is missing"));
        boolean sourceBlocked = throttleService.recordFailure(email, context.ipAddress());
        boolean accountLocked = false;
        if (!sourceBlocked && account.status() == UserAccountStatus.ACTIVE) {
            accountLocked = account.recordFailedLogin(MAXIMUM_FAILED_ATTEMPTS, ACCOUNT_LOCK_DURATION, clock.instant());
        }
        auditService.record(SecurityAuditEventType.LOGIN_FAILED, userId, null, context, "invalid credentials");
        if (accountLocked) {
            sessionService.revokeActiveSessions(userId, "account locked", context);
            auditService.record(SecurityAuditEventType.ACCOUNT_LOCKED, userId, null, context, "failed login limit reached");
        }
        return LoginResult.failed(LoginFailureReason.AUTHENTICATION_FAILED);
    }

    @Transactional
    public LoginResult completeLogin(
        UUID userId,
        Instant expectedPasswordChangedAt,
        String email,
        SecurityContextData context
    ) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalStateException("user account is missing"));
        Credential credential = credentialRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("credential is missing for user " + userId));
        Instant now = clock.instant();

        if (!Objects.equals(credential.passwordChangedAt(), expectedPasswordChangedAt) || !account.canAuthenticate(now)) {
            auditService.record(SecurityAuditEventType.LOGIN_FAILED, userId, null, context, "authentication state changed");
            return LoginResult.failed(LoginFailureReason.AUTHENTICATION_FAILED);
        }

        throttleService.clear(email, context.ipAddress());
        account.recordSuccessfulLogin(now);
        if (mfaMethodRepository.existsByUserIdAndStatus(userId, MfaStatus.ENABLED)) {
            auditService.record(SecurityAuditEventType.LOGIN_FAILED, userId, null, context, "MFA_REQUIRED");
            return LoginResult.failed(LoginFailureReason.MFA_REQUIRED);
        }

        TokenValue token = tokenCodec.generate();
        UserSession session = UserSession.create(
            userId,
            token.tokenHash(),
            context.ipAddress(),
            context.userAgent(),
            SESSION_LIFETIME,
            now
        );
        sessionRepository.save(session);
        Set<Role> roles = roleGrantRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
            .map(grant -> grant.role())
            .collect(Collectors.toUnmodifiableSet());
        auditService.record(SecurityAuditEventType.LOGIN_SUCCEEDED, userId, session.id(), context, "login succeeded");
        return LoginResult.succeeded(userId, token.rawToken(), session.expiresAt(), roles);
    }
}
