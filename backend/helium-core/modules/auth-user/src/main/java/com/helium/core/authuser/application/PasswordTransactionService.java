package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Credential;
import com.helium.core.authuser.domain.PasswordResetToken;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.UserAccountStatus;
import com.helium.core.authuser.infrastructure.CredentialRepository;
import com.helium.core.authuser.infrastructure.PasswordResetTokenRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordTransactionService {
    private final UserAccountRepository userAccountRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final TokenCodec tokenCodec;
    private final SessionService sessionService;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public PasswordTransactionService(
        UserAccountRepository userAccountRepository,
        CredentialRepository credentialRepository,
        PasswordResetTokenRepository resetTokenRepository,
        TokenCodec tokenCodec,
        SessionService sessionService,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.tokenCodec = tokenCodec;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPasswordHash, SecurityContextData securityContext) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(tokenCodec.hash(rawToken))
            .orElseThrow(() -> new AuthValidationException("password reset token is invalid"));
        Instant now = clock.instant();
        token.consume(now);
        UserAccount account = userAccountRepository.findByIdForUpdate(token.userId())
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        Credential credential = credentialRepository.findByUserId(account.id())
            .orElseThrow(() -> new IllegalStateException("credential is missing for user " + account.id()));
        credential.changePassword(newPasswordHash, now);
        if (account.status() == UserAccountStatus.LOCKED) {
            account.unlock(now);
            auditService.record(SecurityAuditEventType.ACCOUNT_UNLOCKED, account.id(), null, securityContext, "password reset unlocked account");
        }
        resetTokenRepository.findAllByUserIdAndConsumedAtIsNull(account.id()).forEach(activeToken -> activeToken.invalidate(now));
        sessionService.revokeActiveSessions(account.id(), "password reset", securityContext);
        auditService.record(SecurityAuditEventType.PASSWORD_RESET_COMPLETED, account.id(), null, securityContext, "password reset completed");
    }

    @Transactional
    public void changePassword(
        UUID userId,
        Instant expectedPasswordChangedAt,
        String newPasswordHash,
        SecurityContextData securityContext
    ) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        Credential credential = credentialRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("credential is missing for user " + userId));
        if (!Objects.equals(credential.passwordChangedAt(), expectedPasswordChangedAt)) {
            throw new AuthValidationException("credential changed during password update");
        }
        credential.changePassword(newPasswordHash, clock.instant());
        sessionService.revokeActiveSessions(account.id(), "password changed", securityContext);
        auditService.record(SecurityAuditEventType.PASSWORD_CHANGED, account.id(), null, securityContext, "password changed");
    }
}
