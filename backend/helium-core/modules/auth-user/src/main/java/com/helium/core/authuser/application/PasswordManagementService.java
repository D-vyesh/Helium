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
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordManagementService implements PasswordManagementPort {
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final UserAccountRepository userAccountRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenCodec tokenCodec;
    private final PasswordTransactionService transactionService;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public PasswordManagementService(
        UserAccountRepository userAccountRepository,
        CredentialRepository credentialRepository,
        PasswordResetTokenRepository resetTokenRepository,
        PasswordHasher passwordHasher,
        TokenCodec tokenCodec,
        PasswordTransactionService transactionService,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordHasher = passwordHasher;
        this.tokenCodec = tokenCodec;
        this.transactionService = transactionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PasswordResetRequestResult requestReset(String email, SecurityContextData securityContext) {
        TokenValue token = tokenCodec.generate();
        UserAccount account = userAccountRepository.findByEmail(UserAccount.normalizeEmail(email)).orElse(null);
        if (account != null && account.status() != UserAccountStatus.CLOSED) {
            resetTokenRepository.save(PasswordResetToken.issue(
                account.id(),
                token.tokenHash(),
                RESET_TOKEN_LIFETIME,
                clock.instant()
            ));
            auditService.record(
                SecurityAuditEventType.PASSWORD_RESET_REQUESTED,
                account.id(),
                null,
                securityContext,
                "password reset token issued"
            );
        } else {
            auditService.record(
                SecurityAuditEventType.PASSWORD_RESET_REQUESTED,
                null,
                null,
                securityContext,
                "password reset request accepted"
            );
        }
        return PasswordResetRequestResult.accepted(token.rawToken());
    }

    @Override
    public void resetPassword(String rawToken, String newPassword, SecurityContextData securityContext) {
        PasswordPolicy.validate(newPassword);
        transactionService.resetPassword(rawToken, passwordHasher.hash(newPassword), securityContext);
    }

    @Override
    public void changePassword(UUID userId, String currentPassword, String newPassword, SecurityContextData securityContext) {
        PasswordPolicy.validate(newPassword);
        Credential credential = credentialRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthValidationException("current password is invalid"));
        if (!passwordHasher.matches(currentPassword, credential.passwordHash())) {
            throw new AuthValidationException("current password is invalid");
        }
        transactionService.changePassword(
            userId,
            credential.passwordChangedAt(),
            passwordHasher.hash(newPassword),
            securityContext
        );
    }
}
